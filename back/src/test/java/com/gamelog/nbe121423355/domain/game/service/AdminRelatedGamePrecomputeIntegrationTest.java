package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRequest;
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse;
import com.gamelog.nbe121423355.domain.game.entity.*;
import com.gamelog.nbe121423355.domain.game.repository.*;
import com.gamelog.nbe121423355.domain.game.service.model.*;
import com.gamelog.nbe121423355.global.exception.ServiceException;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static java.time.Duration.ofSeconds;

@SpringBootTest
@MySqlIntegrationTest
class AdminRelatedGamePrecomputeIntegrationTest {
    private static final AtomicLong IGDB_IDS = new AtomicLong(3_900_000);
    @Autowired AdminRelatedGamePrecomputeService service;
    @Autowired RelatedGamePrecomputeRunQueryService query;
    @Autowired RelatedGamePrecomputeRecoveryService recovery;
    @Autowired RelatedGamePrecomputeRunStore store;
    @Autowired RelatedGamePrecomputeRunRepository runs;
    @Autowired RelatedGamePrecomputeRunFailureRepository failures;
    @Autowired RelatedGamePrecomputeLock lock;
    @Autowired GameRepository games;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean RelatedGamePrecomputeBatchService batch;
    @MockitoSpyBean RelatedGamePrecomputeService calculation;

    private final List<String> runIds = new ArrayList<>();
    private final List<Long> gameIds = new ArrayList<>();
    private final List<CountDownLatch> releases = new ArrayList<>();

    @AfterEach
    void cleanUpCommittedFixtures() {
        releases.forEach(CountDownLatch::countDown);
        await().atMost(ofSeconds(5)).until(() -> !service.status().localRequestActive());
        for (String id : runIds) {
            jdbc.update("DELETE FROM related_game_precompute_run_failures WHERE run_id = ?", id);
            jdbc.update("DELETE FROM related_game_precompute_runs WHERE id = ?", id);
        }
        gameIds.forEach(games::deleteById);
    }

    @Test
    void acceptsWithoutWaitingAndRejectsSecondLocalExecution() throws Exception {
        // given: 첫 게임 계산을 잠시 멈춰 비동기 실행 상태를 관찰할 수 있게 한다.
        long gameId = createGame();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        releases.add(release);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("테스트 대기 초과");
            return invocation.callRealMethod();
        }).when(calculation).precompute(gameId, RelatedGamePrecomputeMode.REFRESH);

        // when: 첫 실행을 접수하고 계산이 끝나기 전에 두 번째 실행을 요청한다.
        var accepted = start(RelatedGamePrecomputeTargetType.IDS, Long.toString(gameId), null);
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        var conflict = catchThrowable(() -> service.start(7, request(RelatedGamePrecomputeTargetType.ALL, null, null)));

        // then: 접수 응답은 ACCEPTED이고 실제 worker만 잠금을 보유하며 추가 요청은 409다.
        assertThat(accepted.status()).isEqualTo(RelatedGamePrecomputeRunStatus.ACCEPTED);
        assertThat(lock.isLocked()).isTrue();
        assertThat(query.detail(accepted.runId()).status()).isEqualTo(RelatedGamePrecomputeRunStatus.RUNNING);
        assertThat(conflict).isInstanceOfSatisfying(ServiceException.class,
                e -> assertThat(e.getResultCode()).isEqualTo("409-10"));
        release.countDown();
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(query.detail(accepted.runId()).status()).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED));
    }

    @Test
    void recordsRejectedWhenAnotherProcessWinsLockAfterAdmission() throws Exception {
        // given: 접수와 실제 배치 잠금 획득 사이를 테스트에서 제어한다.
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        releases.add(release);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("테스트 대기 초과");
            return invocation.callRealMethod();
        }).when(batch).run(any(RelatedGamePrecomputeTarget.class), eq(RelatedGamePrecomputeMode.REFRESH), any());

        // when: 접수 후 다른 MySQL 세션이 실행 잠금을 먼저 획득한다.
        var accepted = start(RelatedGamePrecomputeTargetType.UNREADY, null, null);
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        lock.tryRun(check -> {
            release.countDown();
            await().atMost(ofSeconds(5)).untilAsserted(() ->
                    assertThat(query.detail(accepted.runId()).status()).isEqualTo(RelatedGamePrecomputeRunStatus.REJECTED));
            return true;
        });

        // then: 계산 0건의 실행 거절을 기록하고 자동으로 기다렸다 실행하지 않는다.
        var rejected = query.detail(accepted.runId());
        assertThat(rejected.processed()).isZero();
        assertThat(rejected.finishedAt()).isNotNull();
        verifyNoInteractions(calculation);
    }

    @Test
    void recoveryDefersWhileLockIsHeldAndDoesNotResumeCalculation() {
        // given: 오래된 RUNNING 이력과 아직 살아 있는 ACCEPTED 이력을 준비한다.
        String staleId = manualRun();
        store.start(staleId);
        store.prepared(staleId, 10L, 10);
        makeStale(staleId);
        String freshId = manualRun();

        // when: 다른 실행의 잠금 보유 중 복구를 시도하고 잠금 해제 후 다시 점검한다.
        int deferred = lock.tryRun(check -> {
            assertThat(recovery.recover()).isZero();
            assertThat(query.detail(staleId).status()).isEqualTo(RelatedGamePrecomputeRunStatus.RUNNING);
            assertThat(service.status().recoveryDeferred()).isTrue();
            return 0;
        }).orElseThrow();
        int recovered = recovery.recover();

        // then: 실제 잠금이 없어진 뒤 오래된 이력만 중단으로 정리하고 계산은 재개하지 않는다.
        assertThat(deferred).isZero();
        assertThat(recovered).isEqualTo(1);
        assertThat(query.detail(staleId).status()).isEqualTo(RelatedGamePrecomputeRunStatus.INTERRUPTED);
        assertThat(query.detail(freshId).status()).isEqualTo(RelatedGamePrecomputeRunStatus.ACCEPTED);
        verifyNoInteractions(calculation);
    }

    @Test
    void interruptionRechecksHeartbeatAfterCandidateSelection() {
        // given: 복구 대상으로 선정된 이후 생존 신호가 갱신된 실행을 준비한다.
        String runId = manualRun();
        makeStale(runId);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        String owner = runs.findById(runId).orElseThrow().getOwnerId();
        store.heartbeat(runId, owner);

        // when: 공통 잠금을 획득한 복구 구간에서 이전 기준 시각으로 중단 처리를 시도한다.
        boolean interrupted = lock.tryRun(check -> store.interruptStale(runId, cutoff)).orElseThrow();

        // then: 행을 다시 읽어 갱신된 생존 신호를 확인하고 중단하지 않는다.
        assertThat(interrupted).isFalse();
        assertThat(query.detail(runId).status()).isEqualTo(RelatedGamePrecomputeRunStatus.ACCEPTED);
    }

    @Test
    void replayCreatesNewRunAndPreservesOriginalResult() {
        // given: 지정 게임의 첫 갱신이 정상 종료된 상태를 준비한다.
        long gameId = createGame();
        var first = start(RelatedGamePrecomputeTargetType.IDS, Long.toString(gameId), null);
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(query.detail(first.runId()).status()).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED));
        await().atMost(ofSeconds(5)).until(() -> !service.status().localRequestActive());

        // when: 원본 실행 ID를 연결하여 같은 종류·게임으로 다시 요청한다.
        var replay = start(RelatedGamePrecomputeTargetType.IDS, Long.toString(gameId), first.runId());
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(query.detail(replay.runId()).status()).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED));

        // then: 원본을 덮어쓰지 않고 새 실행에 재요청 연결 정보를 저장한다.
        assertThat(replay.runId()).isNotEqualTo(first.runId());
        assertThat(query.detail(replay.runId()).sourceRunId()).isEqualTo(first.runId());
        assertThat(query.detail(first.runId()).status()).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED);
    }

    @Test
    void historyAndFailuresArePagedAndInternalOwnerIsNotExposed() {
        // given: 두 실행과 실패 상세 2개를 준비한다.
        String failedId = manualRun();
        store.start(failedId);
        store.prepared(failedId, null, 2);
        store.failure(failedId, 20L, new RelatedGamePrecomputeObserver.Progress(1, 0, 1), new IllegalStateException("원문"));
        store.failure(failedId, 10L, new RelatedGamePrecomputeObserver.Progress(2, 0, 2), new IllegalStateException("원문"));
        store.complete(failedId, new RelatedGamePrecomputeObserver.Progress(2, 0, 2));
        manualRun();

        // when: 한 행씩 이력과 실패 상세를 조회하고 잘못된 페이지도 요청한다.
        var history = query.history(0, 1);
        var detail = query.failures(failedId, 1, 1);
        var invalidPage = catchThrowable(() -> query.history(-1, 20));
        var missing = catchThrowable(() -> query.detail("missing"));

        // then: 전체 개수·안정적인 정렬과 오류 코드를 반환하며 엔티티를 직접 노출하지 않는다.
        assertThat(history.content()).hasSize(1);
        assertThat(history.totalElements()).isEqualTo(2);
        assertThat(detail.content().getFirst().gameId()).isEqualTo(20);
        assertThat(detail.totalElements()).isEqualTo(2);
        assertThat(invalidPage).isInstanceOfSatisfying(ServiceException.class,
                e -> assertThat(e.getResultCode()).isEqualTo("400-10"));
        assertThat(missing).isInstanceOfSatisfying(ServiceException.class,
                e -> assertThat(e.getResultCode()).isEqualTo("404-10"));
    }

    private AdminRelatedGamePrecomputeRequest request(RelatedGamePrecomputeTargetType type, String ids, String source) {
        return new AdminRelatedGamePrecomputeRequest(type, ids, source);
    }

    private com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRunResponse start(
            RelatedGamePrecomputeTargetType type, String ids, String source) {
        var response = service.start(7, request(type, ids, source));
        runIds.add(response.runId());
        return response;
    }

    private String manualRun() {
        String id = store.create(7, new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL, List.of()), "other-owner");
        runIds.add(id);
        return id;
    }

    private void makeStale(String runId) {
        jdbc.update("UPDATE related_game_precompute_runs SET heartbeat_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)), runId);
    }

    private long createGame() {
        long igdbId = IGDB_IDS.incrementAndGet();
        var game = Game.createFromIgdb(new IgdbGameResponse(igdbId, "Admin API " + igdbId,
                null, null, 1704067200L, BigDecimal.valueOf(80), List.of(), List.of(), List.of(), List.of()));
        long id = games.saveAndFlush(game).getId();
        gameIds.add(id);
        return id;
    }
}
