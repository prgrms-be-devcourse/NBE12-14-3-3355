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
    @Autowired RelatedGameChangeExpansion expansion;
    @Autowired RelatedGameChangeRecorder recorder;
    @Autowired RelatedGameInputChangeRepository changes;
    @Autowired RelatedGameRefreshTargetRepository targets;
    @Autowired RelatedGamePrecomputeStore store;
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
        preparation.expandPending(() -> {});

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
            var genre = Genre.createFromIgdb(IDS.incrementAndGet(), "Changed genre");
            em.persist(genre); em.flush(); genres.add(genre.getId()); return genre.getId();
        });
    }
    private void link(long gameId, long genreId) {
        tx(() -> { em.persist(new GameGenre(em.getReference(Game.class, gameId), em.getReference(Genre.class, genreId))); return null; });
    }
}
