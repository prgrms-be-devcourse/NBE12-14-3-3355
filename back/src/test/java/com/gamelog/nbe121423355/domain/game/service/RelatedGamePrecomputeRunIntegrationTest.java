package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse;
import com.gamelog.nbe121423355.domain.game.entity.*;
import com.gamelog.nbe121423355.domain.game.repository.*;
import com.gamelog.nbe121423355.domain.game.service.model.*;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import javax.sql.DataSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@MySqlIntegrationTest
class RelatedGamePrecomputeRunIntegrationTest {
    private static final AtomicLong IGDB_IDS = new AtomicLong(2_900_000);

    @Autowired private RelatedGamePrecomputeBatchService batch;
    @Autowired private RelatedGamePrecomputeRunStore store;
    @Autowired private RelatedGamePrecomputeRunTracker tracker;
    @Autowired private RelatedGamePrecomputeTargetRepository targetRepository;
    @Autowired private RelatedGamePrecomputeRunRepository runRepository;
    @Autowired private RelatedGamePrecomputeRunFailureRepository failureRepository;
    @Autowired private GameRepository gameRepository;
    @Autowired private RelatedGameCalculationRepository calculationRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean private RelatedGamePrecomputeService precomputeService;

    private final List<Long> gameIds = new ArrayList<>();
    private final List<String> runIds = new ArrayList<>();

    @AfterEach
    void cleanUpCommittedFixtures() {
        for (String runId : runIds) {
            jdbcTemplate.update("DELETE FROM related_game_precompute_run_failures WHERE run_id = ?", runId);
            jdbcTemplate.update("DELETE FROM related_game_precompute_runs WHERE id = ?", runId);
        }
        for (Long gameId : gameIds) gameRepository.deleteById(gameId);
    }

    @Test
    void distinguishesFailedFromUnreadyIncludingMissingStateAndRespectsPageBoundary() {
        // given: 상태 없음·대기·실행 중·실패·성공·성공 후 실패 게임을 준비한다.
        long missing = createGame();
        long pending = createGame();
        long running = createGame();
        long failed = createGame();
        long ready = createGame();
        long readyFailed = createGame();
        saveCalculation(pending, false, RelatedGameCalculationStatus.PENDING);
        saveCalculation(running, false, RelatedGameCalculationStatus.RUNNING);
        saveCalculation(failed, false, RelatedGameCalculationStatus.FAILED);
        saveCalculation(ready, true, RelatedGameCalculationStatus.SUCCESS);
        saveCalculation(readyFailed, true, RelatedGameCalculationStatus.FAILED);
        long upperId = gameRepository.findMaxGameIdForPrecompute();

        // when: 실패·미완료 대상과 ID 상한·페이지 경계를 적용한 대상을 조회한다.
        var failedIds = targetRepository.findIds(RelatedGamePrecomputeTargetType.FAILED, 0, upperId, 100);
        var unreadyIds = targetRepository.findIds(RelatedGamePrecomputeTargetType.UNREADY, 0, upperId, 100);
        var bounded = targetRepository.findIds(RelatedGamePrecomputeTargetType.UNREADY, missing, running, 1);

        // then: 실패와 미완료를 구분하며 페이지 크기·시작/종료 ID를 유지한다.
        assertThat(failedIds).containsExactly(failed, readyFailed);
        assertThat(unreadyIds).containsExactly(missing, pending, running, failed);
        assertThat(bounded).containsExactly(pending);
        assertThat(targetRepository.count(RelatedGamePrecomputeTargetType.FAILED, upperId)).isEqualTo(2);
        assertThat(targetRepository.count(RelatedGamePrecomputeTargetType.UNREADY, upperId)).isEqualTo(4);
        assertThat(targetRepository.count(RelatedGamePrecomputeTargetType.FAILED, ready)).isEqualTo(1);
    }

    @Test
    void persistsFinalCountsAndRetainsFailureAfterGameDeletion() {
        // given: 두 게임 중 두 번째 계산만 실패하도록 준비한다.
        long first = createGame();
        long second = createGame();
        var target = target(RelatedGamePrecomputeTargetType.IDS, List.of(first, second));
        String runId = createRun(target);
        doThrow(new IllegalStateException("내부 오류 원문")).when(precomputeService)
                .precompute(second, RelatedGamePrecomputeMode.REFRESH);

        // when: 실제 MySQL 잠금과 실행 이력 저장을 연결해 계산한다.
        var result = batch.run(target, RelatedGamePrecomputeMode.REFRESH, tracker.observer(runId));

        // then: 순회를 끝내고 집계·실패 상세를 보존하며 내부 오류 원문을 노출하지 않는다.
        var run = runRepository.findById(runId).orElseThrow();
        var failures = failureRepository.findByRun_IdOrderByGameIdAsc(runId, PageRequest.of(0, 20));
        assertThat(result.processed()).isEqualTo(2);
        assertThat(run.getStatus()).isEqualTo(RelatedGamePrecomputeRunStatus.PARTIAL_FAILED);
        assertThat(run.getProcessed()).isEqualTo(2);
        assertThat(run.getSucceeded()).isEqualTo(1);
        assertThat(run.getFailed()).isEqualTo(1);
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(failures.getContent()).hasSize(1);
        assertThat(failures.getContent().getFirst().getGameId()).isEqualTo(second);
        assertThat(failures.getContent().getFirst().getError())
                .contains("IllegalStateException").doesNotContain("내부 오류 원문");
        gameRepository.deleteById(second);
        assertThat(failureRepository.findByRun_IdOrderByGameIdAsc(runId, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void emptyUnreadyTargetsCompleteSuccessfully() {
        // given: 계산할 게임이 없는 DB에 미완료 복구 실행을 기록한다.
        var target = target(RelatedGamePrecomputeTargetType.UNREADY, List.of());
        String runId = createRun(target);

        // when: 미완료 복구를 실행한다.
        batch.run(target, RelatedGamePrecomputeMode.REFRESH, tracker.observer(runId));

        // then: 0건을 정상 성공으로 저장하고 계산 서비스는 호출하지 않는다.
        var run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED);
        assertThat(run.getEstimatedTotal()).isZero();
        assertThat(run.getProcessed()).isZero();
        verifyNoInteractions(precomputeService);
    }

    @Test
    void runCreationCommitsIndependentlyFromCallerRollback() {
        // given: 실행 이력을 만든 호출자의 트랜잭션이 실패하도록 준비한다.
        var transaction = new TransactionTemplate(transactionManager);
        var target = target(RelatedGamePrecomputeTargetType.ALL, List.of());

        // when: 이력 생성 이후 호출자 트랜잭션을 롤백한다.
        assertThatThrownBy(() -> transaction.execute(status -> {
            createRun(target);
            throw new IllegalStateException("호출자 롤백");
        })).isInstanceOf(IllegalStateException.class);

        // then: 독립 트랜잭션으로 커밋한 실행 이력은 유지된다.
        assertThat(runRepository.findById(runIds.getFirst())).isPresent();
    }

    @Test
    void rollsBackFailureCountTogetherWithRejectedDuplicateDetail() {
        // given: 이미 실패 상세 1건을 저장한 실행을 준비한다.
        String runId = createRun(target(RelatedGamePrecomputeTargetType.IDS, List.of(10L)));
        store.start(runId);
        store.prepared(runId, null, 1);
        store.failure(runId, 10L, progress(1, 0, 1), new IllegalStateException("첫 실패"));

        // when: 같은 실행·게임의 중복 상세 저장으로 유일 제약 위반을 유발한다.
        assertThatThrownBy(() -> store.failure(runId, 10L, progress(2, 0, 2),
                new IllegalStateException("중복 실패"))).isInstanceOf(RuntimeException.class);

        // then: 상세 저장과 같은 트랜잭션의 집계 증가도 함께 롤백된다.
        var run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getProcessed()).isEqualTo(1);
        assertThat(run.getFailed()).isEqualTo(1);
        assertThat(failureRepository.findByRun_IdOrderByGameIdAsc(runId, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void heartbeatDoesNotReplaceSavedProgressOrAcceptAnotherOwner() {
        // given: 진행 집계를 저장한 관리자 실행을 준비한다.
        String runId = createRun(target(RelatedGamePrecomputeTargetType.ALL, List.of()));
        store.start(runId);
        store.prepared(runId, 100L, 100L);
        store.progress(runId, progress(10, 10, 0));
        var before = runRepository.findById(runId).orElseThrow();

        // when: 다른 소유자와 현재 소유자의 생존 신호를 차례로 전달한다.
        store.heartbeat(runId, "another-owner");
        var ignored = runRepository.findById(runId).orElseThrow();
        store.heartbeat(runId, before.getOwnerId());

        // then: 다른 소유자는 무시하고 현재 생존 시각만 갱신하며 집계는 보존한다.
        var after = runRepository.findById(runId).orElseThrow();
        assertThat(ignored.getHeartbeatAt()).isEqualTo(before.getHeartbeatAt());
        assertThat(after.getHeartbeatAt()).isAfterOrEqualTo(before.getHeartbeatAt());
        assertThat(after.getProcessed()).isEqualTo(10);
        assertThat(after.getProgressUpdatedAt()).isEqualTo(before.getProgressUpdatedAt());
    }


    @Test
    void historyFailureDoesNotRollBackCommittedGameCalculation() {
        // given: 실제 계산은 성공하지만 그 직후 진행 통지가 실패하도록 준비한다.
        long first = createGame();
        long second = createGame();
        var target = target(RelatedGamePrecomputeTargetType.IDS, List.of(first, second));
        String runId = createRun(target);
        var delegate = tracker.observer(runId);
        var recordingFailure = new IllegalStateException("이력 연결 오류");
        var observer = new RelatedGamePrecomputeObserver() {
            @Override public void onAcquired() { delegate.onAcquired(); }
            @Override public void onPrepared(Long upperId, long estimatedTotal) {
                delegate.onPrepared(upperId, estimatedTotal);
            }
            @Override public void onProcessed(long gameId, Progress progress, RuntimeException failure) {
                delegate.onProcessed(gameId, progress, failure);
                throw recordingFailure;
            }
            @Override public void onFailed(RuntimeException failure) { delegate.onFailed(failure); }
        };

        // when: 첫 게임 계산의 커밋 이후 실행 전체를 중단한다.
        assertThatThrownBy(() -> batch.run(target, RelatedGamePrecomputeMode.REFRESH, observer))
                .isSameAs(recordingFailure);

        // then: 계산 성공 상태는 유지하고 다음 게임은 계산하지 않으며 실행 이력만 실패로 남긴다.
        var calculation = calculationRepository.findById(first).orElseThrow();
        assertThat(calculation.getLastAttemptStatus()).isEqualTo(RelatedGameCalculationStatus.SUCCESS);
        assertThat(calculation.getLastSuccessAt()).isNotNull();
        assertThat(calculationRepository.findById(second)).isEmpty();
        var run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RelatedGamePrecomputeRunStatus.FAILED);
        assertThat(run.getSucceeded()).isEqualTo(1);
        assertThat(run.getFailed()).isZero();
    }


    @Test
    void migrationSqlCreatesHistoryTablesUsableByJpa() {
        // given: 이 클래스 전용 MySQL 임시 DB에 이력 데이터가 없음을 확인한다.
        assertThat(runRepository.count()).isZero();
        assertThat(failureRepository.count()).isZero();

        // when: 자동 생성된 이력 테이블만 배포용 DDL로 다시 만든다.
        jdbcTemplate.execute("DROP TABLE related_game_precompute_run_failures");
        jdbcTemplate.execute("DROP TABLE related_game_precompute_runs");
        new ResourceDatabasePopulator(new FileSystemResource(
                "../docs/sql/add-related-game-precompute-runs.sql")).execute(dataSource);
        String runId = createRun(target(RelatedGamePrecomputeTargetType.IDS, List.of(10L)));
        store.start(runId);
        store.prepared(runId, null, 1);
        store.failure(runId, 10L, progress(1, 0, 1), new IllegalStateException("검증용 오류"));
        store.complete(runId, progress(1, 0, 1));

        // then: 배포 DDL에서도 JPA의 모든 실행 필드·외래키·실패 상세 저장이 동작한다.
        var run = runRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RelatedGamePrecomputeRunStatus.PARTIAL_FAILED);
        assertThat(run.getFailed()).isEqualTo(1);
        assertThat(failureRepository.findByRun_IdOrderByGameIdAsc(runId, PageRequest.of(0, 20))
                .getTotalElements()).isEqualTo(1);
    }

    private RelatedGamePrecomputeTarget target(RelatedGamePrecomputeTargetType type, List<Long> ids) {
        return new RelatedGamePrecomputeTarget(type, ids);
    }

    private RelatedGamePrecomputeObserver.Progress progress(long processed, long succeeded, long failed) {
        return new RelatedGamePrecomputeObserver.Progress(processed, succeeded, failed);
    }

    private String createRun(RelatedGamePrecomputeTarget target) {
        String runId = store.create(1L, target, UUID.randomUUID().toString());
        runIds.add(runId);
        return runId;
    }

    private long createGame() {
        long igdbId = IGDB_IDS.incrementAndGet();
        var game = Game.createFromIgdb(new IgdbGameResponse(igdbId, "Admin precompute " + igdbId,
                null, null, 1704067200L, BigDecimal.valueOf(80), List.of(), List.of(), List.of(), List.of()));
        long gameId = gameRepository.saveAndFlush(game).getId();
        gameIds.add(gameId);
        return gameId;
    }

    private void saveCalculation(long gameId, boolean succeededBefore, RelatedGameCalculationStatus finalStatus) {
        new TransactionTemplate(transactionManager).execute(status -> {
            var game = entityManager.getReference(Game.class, gameId);
            var calculation = new RelatedGameCalculation(game);
            calculationRepository.save(calculation);
            var now = LocalDateTime.now();
            if (succeededBefore) {
                calculation.start(now);
                calculation.succeed(now);
            }
            if (finalStatus == RelatedGameCalculationStatus.RUNNING) {
                calculation.start(now);
            } else if (finalStatus == RelatedGameCalculationStatus.FAILED) {
                calculation.start(now);
                calculation.fail(now, "이전 계산 실패");
            }
            entityManager.flush();
            return null;
        });
    }
}
