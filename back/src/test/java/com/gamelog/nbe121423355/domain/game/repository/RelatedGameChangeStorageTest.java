package com.gamelog.nbe121423355.domain.game.repository;

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse;
import com.gamelog.nbe121423355.domain.game.entity.Game;
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameInputChange;
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameRefreshTarget;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@MySqlIntegrationTest
class RelatedGameChangeStorageTest {
    private static final AtomicLong IGDB_IDS = new AtomicLong(3_500_000);
    private static final AtomicLong SOURCE_IDS = new AtomicLong(9_000_000_000L);
    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 10, 23, 16, 0);

    @Autowired private RelatedGameInputChangeRepository changes;
    @Autowired private RelatedGameRefreshTargetRepository targets;
    @Autowired private GameRepository gameRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;

    private final List<Long> gameIds = new ArrayList<>();
    private final List<Long> sourceIds = new ArrayList<>();

    @AfterEach
    void cleanUpCommittedFixtures() {
        for (long gameId : sourceIds) {
            jdbcTemplate.update("DELETE FROM related_game_input_changes WHERE game_id = ?", gameId);
        }
        for (long gameId : gameIds) {
            jdbcTemplate.update("DELETE FROM games WHERE id = ?", gameId);
        }
    }

    @Test
    void mergesGenresAndReasonsWithoutLosingIntermediateChanges() {
        // given: 첫 변경에 중복 장르가 있고 다음 변경의 시각은 더 오래된 원천을 준비한다.
        long sourceId = newSourceId();
        inTransaction(() -> changes.record(sourceId, List.of(3L, 1L, 3L), Set.of(GAME_INFORMATION), CHANGED_AT));

        // when: 같은 게임의 중간 장르와 리뷰 변경을 추가로 기록한다.
        long version = inTransaction(() -> changes.record(sourceId, List.of(2L, 3L),
                Set.of(REVIEW), CHANGED_AT.minusMinutes(1)));

        // then: 버전을 증가시키고 장르 합집합·모든 이유·가장 늦은 변경 시각을 보존한다.
        var change = changes.findByGameId(sourceId);
        assertThat(version).isEqualTo(2);
        assertThat(change.getChangeVersion()).isEqualTo(2);
        assertThat(change.getExpandedVersion()).isZero();
        assertThat(change.getAffectedGenreIds()).containsExactly(1L, 2L, 3L);
        assertThat(change.getReasonMask()).isEqualTo(GAME_INFORMATION.getMask() | REVIEW.getMask());
        assertThat(change.getChangedAt()).isEqualTo(CHANGED_AT);
        assertThat(change.isPending()).isTrue();
    }

    @Test
    void rollsBackOriginalGameAndChangeRecordTogether() {
        // given: 게임 생성과 변경 기록을 함께 저장할 호출자 트랜잭션을 준비한다.
        var failure = new IllegalStateException("원본 저장 실패");

        // when: 원본 게임과 변경 기록을 저장한 뒤 호출자 작업을 실패시킨다.
        assertThatThrownBy(() -> inTransaction(() -> {
            long gameId = createGame();
            sourceIds.add(gameId);
            changes.record(gameId, List.of(1L), Set.of(GAME_ADDED), CHANGED_AT);
            throw failure;
        })).isSameAs(failure);

        // then: 독립 커밋 없이 원본 게임과 변경 원천이 모두 롤백된다.
        assertThat(gameRepository.existsById(gameIds.getFirst())).isFalse();
        assertThat(changes.findByGameId(sourceIds.getFirst())).isNull();
    }

    @Test
    void requiresCallerTransactionForAllMutations() {
        // given: 트랜잭션 없이 저장소를 호출할 양수 ID를 준비한다.
        long sourceId = newSourceId();

        // when: 원천 기록·확장 확인·대기 등록·반영 확인을 트랜잭션 밖에서 호출한다.
        var failures = List.<Runnable>of(
                () -> changes.record(sourceId, List.of(), Set.of(REVIEW), CHANGED_AT),
                () -> changes.markExpanded(sourceId, 1),
                () -> targets.request(sourceId, Set.of(REVIEW), CHANGED_AT),
                () -> targets.markApplied(sourceId, 1));

        // then: 모든 쓰기를 거절하고 원천이나 대기 행을 자동 커밋하지 않는다.
        for (Runnable mutation : failures) {
            assertThatThrownBy(mutation::run).isInstanceOf(IllegalTransactionStateException.class);
        }
        assertThat(changes.findByGameId(sourceId)).isNull();
        assertThat(targets.findByGameId(sourceId)).isNull();
    }

    @Test
    void serializesConcurrentFirstRecordsWithoutLosingEitherChange() throws Exception {
        // given: 동일한 신규 원천을 동시에 생성할 두 트랜잭션과 시작 신호를 준비한다.
        long sourceId = newSourceId();
        var start = new CountDownLatch(1);

        // when: 서로 다른 장르·이유를 가진 변경을 별도 스레드에서 함께 기록한다.
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return inTransaction(() -> changes.record(sourceId, List.of(1L), Set.of(REVIEW), CHANGED_AT));
            });
            var second = executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return inTransaction(() -> changes.record(sourceId, List.of(2L), Set.of(USER_GAME_RECORD), CHANGED_AT));
            });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1L, 2L);
        }

        // then: 신규 행 경합에도 두 변경을 한 행에 병합하고 각각의 버전을 반영한다.
        var change = changes.findByGameId(sourceId);
        assertThat(change.getChangeVersion()).isEqualTo(2);
        assertThat(change.getAffectedGenreIds()).containsExactly(1L, 2L);
        assertThat(change.getReasonMask()).isEqualTo(REVIEW.getMask() | USER_GAME_RECORD.getMask());
    }

    @Test
    void mergesLatestLockedStateEvenWhenCallerHasAnOlderReadSnapshot() {
        // given: 첫 원천을 저장하고 호출자 읽기 스냅샷 이후 다른 트랜잭션이 새 변경을 커밋한다.
        long sourceId = newSourceId();
        inTransaction(() -> changes.record(sourceId, List.of(1L), Set.of(GAME_INFORMATION), CHANGED_AT));

        // when: 오래된 스냅샷을 가진 호출자가 세 번째 변경을 기록한다.
        long version = inTransaction(() -> {
            assertThat(changes.findByGameId(sourceId).getChangeVersion()).isEqualTo(1);
            inTransaction(() -> changes.record(sourceId, List.of(2L), Set.of(REVIEW), CHANGED_AT));
            return changes.record(sourceId, List.of(3L), Set.of(USER_GAME_RECORD), CHANGED_AT);
        });

        // then: 현재 읽기 행 잠금으로 중간 커밋까지 병합하여 버전과 장르를 잃지 않는다.
        assertThat(version).isEqualTo(3);
        var change = changes.findByGameId(sourceId);
        assertThat(change.getAffectedGenreIds()).containsExactly(1L, 2L, 3L);
        assertThat(change.getReasonMask()).isEqualTo(
                GAME_INFORMATION.getMask() | REVIEW.getMask() | USER_GAME_RECORD.getMask());
    }

    @Test
    void acknowledgesOnlyCapturedSourceVersionAndKeepsLaterChanges() {
        // given: 버전 1을 캡처한 뒤 버전 2의 장르·이유 변경이 들어온 원천을 준비한다.
        long sourceId = newSourceId();
        long captured = inTransaction(() -> changes.record(sourceId, List.of(1L), Set.of(REVIEW), CHANGED_AT));
        inTransaction(() -> changes.record(sourceId, List.of(2L), Set.of(GAME_INFORMATION), CHANGED_AT));

        // when: 캡처한 버전까지만 확장 완료를 표시한다.
        boolean advanced = inTransaction(() -> changes.markExpanded(sourceId, captured));

        // then: 새 버전은 미확장으로 남고 장르·이유 합집합을 성급하게 비우지 않는다.
        var change = changes.findByGameId(sourceId);
        assertThat(advanced).isTrue();
        assertThat(change.getExpandedVersion()).isEqualTo(1);
        assertThat(change.getChangeVersion()).isEqualTo(2);
        assertThat(change.isPending()).isTrue();
        assertThat(change.getAffectedGenreIds()).containsExactly(1L, 2L);
        assertThat(change.getReasonMask()).isEqualTo(REVIEW.getMask() | GAME_INFORMATION.getMask());
    }

    @Test
    void clearsCompletedSourcePayloadAndNeverResetsItsVersion() {
        // given: 최신 버전까지 확장 완료한 원천을 준비한다.
        long sourceId = newSourceId();
        inTransaction(() -> changes.record(sourceId, List.of(1L), Set.of(REVIEW), CHANGED_AT));
        inTransaction(() -> changes.markExpanded(sourceId, 1));
        var completed = changes.findByGameId(sourceId);

        // when: 완료 후 새 변경을 기록하고 이전 버전 완료 통지가 다시 도착한다.
        long version = inTransaction(() -> changes.record(sourceId, List.of(2L), Set.of(READY), CHANGED_AT));
        boolean staleAcknowledgement = inTransaction(() -> changes.markExpanded(sourceId, 1));

        // then: 완료한 정보만 비우고 새 버전·새 장르·새 이유를 유지한다.
        assertThat(completed.isPending()).isFalse();
        assertThat(completed.getAffectedGenreIds()).isEmpty();
        assertThat(completed.getReasonMask()).isZero();
        assertThat(version).isEqualTo(2);
        assertThat(staleAcknowledgement).isFalse();
        var pending = changes.findByGameId(sourceId);
        assertThat(pending.getExpandedVersion()).isEqualTo(1);
        assertThat(pending.getAffectedGenreIds()).containsExactly(2L);
        assertThat(pending.getReasonMask()).isEqualTo(READY.getMask());
    }

    @Test
    void rejectsFutureSourceAcknowledgementWithoutChangingState() {
        // given: 실제로 기록된 버전은 1인 원천을 준비한다.
        long sourceId = newSourceId();
        inTransaction(() -> changes.record(sourceId, List.of(1L), Set.of(REVIEW), CHANGED_AT));

        // when: 존재하지 않는 버전 2까지 확장 완료 표시를 시도한다.
        assertThatThrownBy(() -> inTransaction(() -> changes.markExpanded(sourceId, 2)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기록된 변경 버전을 넘을 수 없습니다.");

        // then: 아직 미처리인 장르·이유와 확장 완료 버전을 보존한다.
        var pending = changes.findByGameId(sourceId);
        assertThat(pending.getExpandedVersion()).isZero();
        assertThat(pending.getAffectedGenreIds()).containsExactly(1L);
        assertThat(pending.getReasonMask()).isEqualTo(REVIEW.getMask());
    }

    @Test
    void mergesTargetRequestsAndAcknowledgesOnlyCapturedVersion() {
        // given: 같은 기준 게임에 서로 다른 이유의 갱신 요구가 두 번 들어온다.
        long gameId = createGame();
        long captured = inTransaction(() -> targets.request(gameId, Set.of(REVIEW), CHANGED_AT));
        long latest = inTransaction(() -> targets.request(gameId, Set.of(GAME_INFORMATION), CHANGED_AT));

        // when: 먼저 캡처한 요구 버전까지만 반영 완료 처리한다.
        boolean advanced = inTransaction(() -> targets.markApplied(gameId, captured));

        // then: 요구를 한 행에 병합하고 새 버전과 선정 이유를 대기로 유지한다.
        var pending = targets.findByGameId(gameId);
        assertThat(captured).isEqualTo(1);
        assertThat(latest).isEqualTo(2);
        assertThat(advanced).isTrue();
        assertThat(pending.getRequiredVersion()).isEqualTo(2);
        assertThat(pending.getAppliedVersion()).isEqualTo(1);
        assertThat(pending.isPending()).isTrue();
        assertThat(pending.getReasonMask()).isEqualTo(REVIEW.getMask() | GAME_INFORMATION.getMask());
    }

    @Test
    void keepsMonotonicTargetVersionsAcrossCompletionAndRepeatedBaselineRequests() {
        // given: 초기 기준선 요구와 후속 요구를 모두 반영한 기준 게임을 준비한다.
        long gameId = createGame();
        inTransaction(() -> targets.request(gameId, Set.of(INITIAL_BASELINE), CHANGED_AT));
        inTransaction(() -> targets.request(gameId, Set.of(REVIEW), CHANGED_AT));
        inTransaction(() -> targets.markApplied(gameId, 2));
        var completed = targets.findByGameId(gameId);

        // when: 초기 기준선을 재요청하고 더 오래된 완료 통지를 전달한다.
        long next = inTransaction(() -> targets.request(gameId, Set.of(INITIAL_BASELINE), CHANGED_AT.minusMinutes(1)));
        boolean stale = inTransaction(() -> targets.markApplied(gameId, 1));

        // then: 요구 버전을 초기화하지 않고 새 대기와 마지막 변경 시각을 유지한다.
        assertThat(completed.isPending()).isFalse();
        assertThat(completed.getReasonMask()).isZero();
        assertThat(next).isEqualTo(3);
        assertThat(stale).isFalse();
        var pending = targets.findByGameId(gameId);
        assertThat(pending.getAppliedVersion()).isEqualTo(2);
        assertThat(pending.getReasonMask()).isEqualTo(INITIAL_BASELINE.getMask());
        assertThat(pending.getChangedAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    void rejectsFutureTargetAcknowledgementAndRollsBackCompletionWithCaller() {
        // given: 아직 반영되지 않은 기준 게임의 요구 버전 1을 준비한다.
        long gameId = createGame();
        inTransaction(() -> targets.request(gameId, Set.of(REVIEW), CHANGED_AT));

        // when: 미래 버전 완료와, 정상 완료 표시 후 호출자 실패를 각각 시도한다.
        assertThatThrownBy(() -> inTransaction(() -> targets.markApplied(gameId, 2)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("갱신 필요 버전을 넘을 수 없습니다.");
        assertThatThrownBy(() -> inTransaction(() -> {
            targets.markApplied(gameId, 1);
            throw new IllegalStateException("후보 교체 트랜잭션 실패");
        })).isInstanceOf(IllegalStateException.class);

        // then: 잘못된 확인이나 호출자 롤백으로 대기를 지우지 않는다.
        var pending = targets.findByGameId(gameId);
        assertThat(pending.getAppliedVersion()).isZero();
        assertThat(pending.isPending()).isTrue();
        assertThat(pending.getReasonMask()).isEqualTo(REVIEW.getMask());
    }

    @Test
    void rollsBackTargetRequestAndSourceAcknowledgementTogether() {
        // given: 아직 확장하지 않은 원천과 영향받는 기준 게임을 준비한다.
        long sourceId = newSourceId();
        long gameId = createGame();
        inTransaction(() -> changes.record(sourceId, List.of(1L), Set.of(REVIEW), CHANGED_AT));

        // when: 대상 등록과 원천 완료 표시를 같은 트랜잭션에서 수행한 뒤 실패시킨다.
        assertThatThrownBy(() -> inTransaction(() -> {
            targets.request(gameId, Set.of(REVIEW), CHANGED_AT);
            changes.markExpanded(sourceId, 1);
            throw new IllegalStateException("영향 대상 저장 실패");
        })).isInstanceOf(IllegalStateException.class);

        // then: 대상과 원천 확인이 함께 롤백되어 다음 실행에서 원천을 다시 찾을 수 있다.
        assertThat(targets.findByGameId(gameId)).isNull();
        assertThat(changes.findByGameId(sourceId).getExpandedVersion()).isZero();
        assertThat(changes.findByGameId(sourceId).getAffectedGenreIds()).containsExactly(1L);
    }

    @Test
    void preservesDeletedGameSourceAndCascadesItsRefreshTarget() {
        // given: 같은 게임의 삭제 이전 장르와 갱신 대기를 기록한다.
        long gameId = createGame();
        sourceIds.add(gameId);
        inTransaction(() -> changes.record(gameId, List.of(1L, 2L), Set.of(GAME_DELETED), CHANGED_AT));
        inTransaction(() -> targets.request(gameId, Set.of(REVIEW), CHANGED_AT));

        // when: IGDB 삭제처럼 JPA 훅 없이 게임을 지우고 늦은 갱신 요청·완료를 전달한다.
        jdbcTemplate.update("DELETE FROM games WHERE id = ?", gameId);
        Long requested = inTransaction(() -> targets.request(gameId, Set.of(REVIEW), CHANGED_AT));
        boolean applied = inTransaction(() -> targets.markApplied(gameId, 1));

        // then: 삭제 전 정보는 남지만 삭제한 기준 게임의 대기는 되살리지 않는다.
        assertThat(changes.findByGameId(gameId).getAffectedGenreIds()).containsExactly(1L, 2L);
        assertThat(targets.findByGameId(gameId)).isNull();
        assertThat(requested).isNull();
        assertThat(applied).isFalse();
    }

    @Test
    void deploymentSqlSupportsJdbcWritesJpaReadsAndDatabaseConstraints() {
        // given: 이 클래스에서 사용하는 격리 MySQL에 변경·대기 데이터가 없음을 확인한다.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM related_game_input_changes", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM related_game_refresh_targets", Long.class)).isZero();

        // when: 두 테이블만 배포 SQL로 다시 만든 뒤 JDBC로 저장하고 JPA로 읽는다.
        jdbcTemplate.execute("DROP TABLE related_game_refresh_targets");
        jdbcTemplate.execute("DROP TABLE related_game_input_changes");
        new ResourceDatabasePopulator(new FileSystemResource(
                "../docs/sql/add-related-game-change-tracking.sql")).execute(dataSource);
        long gameId = createGame();
        sourceIds.add(gameId);
        inTransaction(() -> {
            changes.record(gameId, List.of(1L, 2L), Set.of(REVIEW), CHANGED_AT);
            targets.request(gameId, Set.of(REVIEW), CHANGED_AT);
            return null;
        });
        var mappedChange = inTransaction(() -> entityManager.find(RelatedGameInputChange.class, gameId));
        var mappedTarget = inTransaction(() -> entityManager.find(RelatedGameRefreshTarget.class, gameId));

        // then: 배포 DDL의 JSON·버전·연결 매핑이 맞고 미래 완료 버전은 DB에서도 거절한다.
        assertThat(mappedChange.getAffectedGenreIds()).containsExactly(1L, 2L);
        assertThat(mappedChange.getChangeVersion()).isEqualTo(1);
        assertThat(mappedChange.getChangedAt()).isEqualTo(CHANGED_AT);
        assertThat(mappedTarget.getRequiredVersion()).isEqualTo(1);
        assertThat(mappedTarget.getChangedAt()).isEqualTo(CHANGED_AT);
        assertThat(mappedTarget.getGame().getId()).isEqualTo(gameId);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE related_game_input_changes SET expanded_version = 2 WHERE game_id = ?", gameId))
                .rootCause().isInstanceOfSatisfying(SQLException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(3819));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE related_game_refresh_targets SET applied_version = 2 WHERE game_id = ?", gameId))
                .rootCause().isInstanceOfSatisfying(SQLException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(3819));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO related_game_refresh_targets VALUES (?, 1, 0, 1, ?)", newSourceId(), CHANGED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private <T> T inTransaction(Supplier<T> work) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> work.get());
    }

    private long newSourceId() {
        long sourceId = SOURCE_IDS.incrementAndGet();
        sourceIds.add(sourceId);
        return sourceId;
    }

    private long createGame() {
        long igdbId = IGDB_IDS.incrementAndGet();
        var game = Game.createFromIgdb(new IgdbGameResponse(igdbId, "Change storage " + igdbId,
                null, null, 1704067200L, BigDecimal.valueOf(80), List.of(), List.of(), List.of(), List.of()));
        long gameId = gameRepository.saveAndFlush(game).getId();
        gameIds.add(gameId);
        return gameId;
    }
}
