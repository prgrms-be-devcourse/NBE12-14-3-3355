package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse;
import com.gamelog.nbe121423355.domain.game.entity.*;
import com.gamelog.nbe121423355.domain.game.repository.*;
import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection;
import com.gamelog.nbe121423355.domain.game.service.model.*;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@MySqlIntegrationTest
class ChangedRelatedGamePrecomputeTest {
    private static final AtomicLong IDS = new AtomicLong(4_900_000);
    @Autowired RelatedGamePrecomputeBatchService batch;
    @Autowired RelatedGameChangePreparation preparation;
    @Autowired RelatedGameChangeQueryService query;
    @Autowired RelatedGameChangeExpansion expansion;
    @Autowired RelatedGameChangeRecorder recorder;
    @Autowired RelatedGameInputChangeRepository changes;
    @Autowired RelatedGameRefreshTargetRepository targets;
    @Autowired RelatedGamePrecomputeStore store;
    @Autowired RelatedGameQueryRepository existingQuery;
    @Autowired RelatedGameCandidateRepository candidates;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private final List<Long> games = new ArrayList<>();
    private final List<Long> genres = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (long id : games) {
            jdbc.update("DELETE FROM game_genres WHERE game_id = ?", id);
            jdbc.update("DELETE FROM games WHERE id = ?", id);
            jdbc.update("DELETE FROM related_game_input_changes WHERE game_id = ?", id);
        }
        for (long id : genres) jdbc.update("DELETE FROM genres WHERE id = ?", id);
    }

    @Test
    void selectsSelfAndBothGenreRelationsIncludingPreviouslyUnstoredCandidates() {
        // given: 변경 전·후 장르를 각각 공유하는 기준 게임과 무관한 게임을 준비한다.
        long oldGenre = genre(), newGenre = genre();
        long source = game(), oldBase = game(), newBase = game(), unrelated = game();
        link(source, oldGenre); link(oldBase, oldGenre); link(newBase, newGenre);
        tx(() -> { recorder.record(source, RelatedGameChangeReason.GAME_INFORMATION); return null; });
        jdbc.update("DELETE FROM game_genres WHERE game_id = ?", source);
        link(source, newGenre);
        tx(() -> { recorder.record(source, RelatedGameChangeReason.GAME_INFORMATION, List.of(oldGenre)); return null; });

        // when: 저장된 후보 목록을 사용하지 않고 변경 원천을 영향 기준 게임으로 확장한다.
        preparation.expandPending(() -> kotlin.Unit.INSTANCE);

        // then: 이전·현재 장르 관계와 변경 게임 자신을 포함하고 무관한 게임은 제외한다.
        assertThat(targets.findPendingIds(0, Long.MAX_VALUE, 100)).containsExactly(source, oldBase, newBase);
        assertThat(targets.findByGameId(unrelated)).isNull();
        assertThat(changes.findByGameId(source).isPending()).isFalse();
    }

    @Test
    void expandingCapturedSourceDoesNotConsumeConcurrentNewVersion() {
        // given: 첫 버전 캡처 이후 새 변경이 커밋된 원천을 준비한다.
        long source = game();
        tx(() -> changes.record(source, List.of(), Set.of(RelatedGameChangeReason.REVIEW), LocalDateTime.now()));
        var captured = changes.findByGameId(source);
        tx(() -> changes.record(source, List.of(), Set.of(RelatedGameChangeReason.USER_GAME_RECORD), LocalDateTime.now()));

        // when: 먼저 캡처한 원천의 영향 대상만 확장한다.
        expansion.expand(captured);

        // then: 대상은 선정하되 새 원천 버전과 변경 이유는 다음 실행에 남긴다.
        assertThat(targets.findByGameId(source).getRequiredVersion()).isEqualTo(1);
        var remaining = changes.findByGameId(source);
        assertThat(remaining.getExpandedVersion()).isEqualTo(1);
        assertThat(remaining.getChangeVersion()).isEqualTo(2);
        assertThat(remaining.isPending()).isTrue();
    }

    @Test
    void changedRunProcessesMultiplePagesAndLeavesNewReadyTransitionsForNextRun() {
        // given: 한 페이지를 초과하는 미계산 기준 게임을 초기 대기에 등록한다.
        for (int i = 0; i < 110; i++) game();
        expansion.initializeBaseline(games.getLast());

        // when: 변경 기반 부분 갱신으로 모든 페이지를 계산한다.
        var result = batch.run(new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.CHANGED),
                RelatedGamePrecomputeMode.REFRESH, RelatedGamePrecomputeObserver.NONE);

        // then: 정상 빈 결과도 성공 처리하고 이번 최초 READY 전환은 다음 원천으로 남긴다.
        assertThat(result.processed()).isEqualTo(110);
        assertThat(result.succeeded()).isEqualTo(110);
        assertThat(targets.countPending(Long.MAX_VALUE)).isZero();
        assertThat(changes.countPending()).isGreaterThanOrEqualTo(110);
    }

    @Test
    void changedStoresSqlEquivalentTopTwentyOnlyForPendingSources() {
        // given: 같은 장르의 READY 게임 25개 중 두 개만 갱신 대기에 등록한다.
        long genre = genre();
        for (int i = 0; i < 25; i++) {
            long id = game();
            link(id, genre);
            var now = LocalDateTime.now();
            store.start(id, now);
            store.replace(id, new ArrayList<>(), now);
        }
        tx(() -> {
            for (long id : games) changes.markExpanded(id, changes.findByGameId(id).getChangeVersion());
            for (long id : games.subList(0, 2)) targets.request(id, Set.of(RelatedGameChangeReason.REVIEW), LocalDateTime.now());
            return null;
        });
        long first = games.getFirst(), second = games.get(1), notPending = games.get(2);
        var expectedFirst = existingQuery.findCandidatesForPrecompute(first, 20, true);
        var expectedSecond = existingQuery.findCandidatesForPrecompute(second, 20, true);

        // when: 전체 후보 정보를 재사용하여 변경 영향 게임만 갱신한다.
        var result = batch.run(new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.CHANGED),
                RelatedGamePrecomputeMode.REFRESH, RelatedGamePrecomputeObserver.NONE);

        // then: 두 게임의 순위·점수가 기존 SQL과 같고 나머지 게임의 저장 결과는 유지된다.
        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(2);
        var actualFirst = candidates.findByIdSourceGameIdOrderByRelatedRankAsc(first);
        var actualSecond = candidates.findByIdSourceGameIdOrderByRelatedRankAsc(second);
        assertThat(actualFirst).hasSize(20);
        assertThat(actualSecond).hasSize(20);
        for (int i = 0; i < 20; i++) {
            assertThat(actualFirst.get(i).getId().getCandidateGameId()).isEqualTo(expectedFirst.get(i).gameId());
            assertThat(actualFirst.get(i).getRelatedScore()).isEqualByComparingTo(expectedFirst.get(i).relatedScore());
            assertThat(actualSecond.get(i).getId().getCandidateGameId()).isEqualTo(expectedSecond.get(i).gameId());
            assertThat(actualSecond.get(i).getRelatedScore()).isEqualByComparingTo(expectedSecond.get(i).relatedScore());
        }
        assertThat(candidates.findByIdSourceGameIdOrderByRelatedRankAsc(notPending)).isEmpty();
        assertThat(targets.findByGameId(notPending)).isNull();
        assertThat(targets.countPending(Long.MAX_VALUE)).isZero();
        assertThat(changes.countPending()).isZero();
    }

    @Test
    void changeAfterPreparationSurvivesSuccessfulCalculation() {
        // given: 갱신 대기 한 건과 준비 완료 직후 원천을 변경할 통지를 준비한다.
        long source = game();
        expansion.initializeBaseline(source);
        var observer = new RelatedGamePrecomputeObserver() {
            @Override public void onPrepared(Long upper, long count) {
                tx(() -> { recorder.record(source, RelatedGameChangeReason.REVIEW); return null; });
            }
        };

        // when: 준비 이후 입력 변경이 있어도 고정된 대상 버전만 계산한다.
        batch.run(new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.CHANGED),
                RelatedGamePrecomputeMode.REFRESH, observer);

        // then: 캡처된 대기는 완료하지만 새 입력 변경은 미확장 원천으로 유지한다.
        assertThat(targets.findByGameId(source).isPending()).isFalse();
        assertThat(changes.findByGameId(source).isPending()).isTrue();
        assertThat(changes.findByGameId(source).getReasonMask() & RelatedGameChangeReason.REVIEW.getMask()).isNotZero();
    }

    @Test
    void completionFailureRollsBackCandidateReplacementAndPreservesPendingVersion() {
        // given: 이전 정상 후보와 버전 1 갱신 대기를 가진 기준 게임을 준비한다.
        long source = game(), previous = game(), replacement = game();
        var now = LocalDateTime.now();
        store.start(source, now);
        store.replace(source, new ArrayList<>(List.of(new RelatedGameCandidateProjection(previous, BigDecimal.TEN))), now);
        tx(() -> targets.request(source, Set.of(RelatedGameChangeReason.REVIEW), now));
        store.start(source, now);

        // when: 후보 INSERT 이후 잘못된 미래 버전 완료 표시로 트랜잭션을 실패시킨다.
        assertThatThrownBy(() -> store.replace(source,
                new ArrayList<>(List.of(new RelatedGameCandidateProjection(replacement, BigDecimal.ONE))), now, 2L))
                .isInstanceOf(RuntimeException.class);

        // then: 후보 교체까지 롤백하고 이전 후보와 미완료 버전을 보존한다.
        assertThat(candidates.findByIdSourceGameIdOrderByRelatedRankAsc(source))
                .extracting(row -> row.getId().getCandidateGameId()).containsExactly(previous);
        assertThat(targets.findByGameId(source).getAppliedVersion()).isZero();
    }

    @Test
    void previewIncludesOldAndCurrentGenresAndPendingTargetsWithoutConsumingChanges() {
        // given: 이전·현재 장르 영향 게임과 중복 대기, 별도 대기, 무관한 게임을 준비한다.
        long oldGenre = genre(), currentGenre = genre();
        long source = game(), oldBase = game(), currentBase = game(), pending = game(), unrelated = game();
        link(source, currentGenre); link(oldBase, oldGenre); link(currentBase, currentGenre);
        tx(() -> changes.record(source, List.of(oldGenre), Set.of(RelatedGameChangeReason.REVIEW), LocalDateTime.now()));
        tx(() -> { targets.request(oldBase, Set.of(RelatedGameChangeReason.REVIEW), LocalDateTime.now());
            targets.request(pending, Set.of(RelatedGameChangeReason.REVIEW), LocalDateTime.now()); return null; });
        var before = changes.findByGameId(source);

        // when: 두 페이지로 변경 영향 게임의 ID와 이름을 미리 조회한다.
        var first = query.preview(0, 2);
        var second = query.preview(1, 2);

        // then: 중복 없이 ID 순서로 이름을 반환하고 변경·대기 버전을 소비하지 않는다.
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).extracting(RelatedGamePreviewTarget::gameId)
                .containsExactly(Long.toString(source), Long.toString(oldBase));
        assertThat(second.content()).extracting(RelatedGamePreviewTarget::gameId)
                .containsExactly(Long.toString(currentBase), Long.toString(pending));
        assertThat(first.content()).allSatisfy(row -> assertThat(row.title()).startsWith("Changed "));
        assertThat(changes.findByGameId(source).getChangeVersion()).isEqualTo(before.getChangeVersion());
        assertThat(changes.findByGameId(source).getExpandedVersion()).isEqualTo(before.getExpandedVersion());
        assertThat(targets.countPending(Long.MAX_VALUE)).isEqualTo(2);
        assertThat(targets.findByGameId(source)).isNull();
        assertThat(targets.findByGameId(unrelated)).isNull();
    }

    @Test
    void previewRetainsDeletedSourceGenreImpactAndValidatesPagination() {
        // given: 삭제된 원천의 이전 장르를 공유하는 게임과 완료된 대기를 준비한다.
        long oldGenre = genre(), source = game(), affected = game(), completed = game();
        link(affected, oldGenre);
        tx(() -> changes.record(source, List.of(oldGenre), Set.of(RelatedGameChangeReason.GAME_DELETED), LocalDateTime.now()));
        jdbc.update("DELETE FROM games WHERE id = ?", source);
        tx(() -> { targets.request(completed, Set.of(RelatedGameChangeReason.REVIEW), LocalDateTime.now());
            targets.markApplied(completed, 1); return null; });

        // when: 삭제된 게임의 영향과 잘못된 페이지 범위를 조회한다.
        var result = query.preview(0, 20);

        // then: 삭제된 자신과 완료된 대기는 제외하고 남은 영향 게임만 반환한다.
        assertThat(result.content()).extracting(RelatedGamePreviewTarget::gameId).containsExactly(Long.toString(affected));
        assertThatThrownBy(() -> query.preview(-1, 20)).isInstanceOf(com.gamelog.nbe121423355.global.exception.ServiceException.class);
        assertThatThrownBy(() -> query.preview(0, 101)).isInstanceOf(com.gamelog.nbe121423355.global.exception.ServiceException.class);
    }

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    private long game() {
        return tx(() -> {
            long igdbId = IDS.incrementAndGet();
            var game = Game.createFromIgdb(new IgdbGameResponse(igdbId, "Changed " + igdbId,
                    null, null, 1704067200L, BigDecimal.valueOf(80), List.of(), List.of(), List.of(), List.of()));
            em.persist(game); em.flush(); games.add(game.getId()); return game.getId();
        });
    }
    private long genre() {
        return tx(() -> {
            var genre = Genre.createFromIgdb(IDS.incrementAndGet(), "Changed genre " + IDS.get());
            em.persist(genre); em.flush(); genres.add(genre.getId()); return genre.getId();
        });
    }
    private void link(long gameId, long genreId) {
        tx(() -> { em.persist(new GameGenre(em.getReference(Game.class, gameId), em.getReference(Genre.class, genreId))); return null; });
    }
}
