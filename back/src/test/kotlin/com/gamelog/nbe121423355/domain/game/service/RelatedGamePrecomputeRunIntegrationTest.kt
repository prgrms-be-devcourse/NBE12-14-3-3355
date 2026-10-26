package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculationStatus
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunFailureRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeTargetRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.ArrayList
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileSystemResource
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@MySqlIntegrationTest
class RelatedGamePrecomputeRunIntegrationTest {

    @Autowired
    private lateinit var batch: RelatedGamePrecomputeBatchService

    @Autowired
    private lateinit var store: RelatedGamePrecomputeRunStore

    @Autowired
    private lateinit var tracker: RelatedGamePrecomputeRunTracker

    @Autowired
    private lateinit var targetRepository: RelatedGamePrecomputeTargetRepository

    @Autowired
    private lateinit var runRepository: RelatedGamePrecomputeRunRepository

    @Autowired
    private lateinit var failureRepository: RelatedGamePrecomputeRunFailureRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var calculationRepository: RelatedGameCalculationRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @MockitoSpyBean
    private lateinit var precomputeService: RelatedGamePrecomputeService
    private val gameIds = mutableListOf<Long>()
    private val runIds = mutableListOf<String>()

    @AfterEach
    fun cleanUpCommittedFixtures() {
        for (runId in runIds) {
            jdbcTemplate.update("DELETE FROM related_game_precompute_run_failures WHERE run_id = ?", runId)
            jdbcTemplate.update("DELETE FROM related_game_precompute_runs WHERE id = ?", runId)
        }
        for (gameId in gameIds) gameRepository.deleteById(gameId)
    }

    @Test
    fun distinguishesFailedFromUnreadyIncludingMissingStateAndRespectsPageBoundary() {
        // given: 상태 없음·대기·실행 중·실패·성공·성공 후 실패 게임을 준비한다.
        val missing = createGame()
        val pending = createGame()
        val running = createGame()
        val failed = createGame()
        val ready = createGame()
        val readyFailed = createGame()
        saveCalculation(pending, false, RelatedGameCalculationStatus.PENDING)
        saveCalculation(running, false, RelatedGameCalculationStatus.RUNNING)
        saveCalculation(failed, false, RelatedGameCalculationStatus.FAILED)
        saveCalculation(ready, true, RelatedGameCalculationStatus.SUCCESS)
        saveCalculation(readyFailed, true, RelatedGameCalculationStatus.FAILED)
        val upperId = requireNotNull(gameRepository.findMaxGameIdForPrecompute())

        // when: 실패·미완료 대상과 ID 상한·페이지 경계를 적용한 대상을 조회한다.
        val failedIds = targetRepository.findIds(RelatedGamePrecomputeTargetType.FAILED, 0, upperId, 100)
        val unreadyIds = targetRepository.findIds(RelatedGamePrecomputeTargetType.UNREADY, 0, upperId, 100)
        val bounded = targetRepository.findIds(RelatedGamePrecomputeTargetType.UNREADY, missing, running, 1)

        // then: 실패와 미완료를 구분하며 페이지 크기·시작/종료 ID를 유지한다.
        assertThat(failedIds).containsExactly(failed, readyFailed)
        assertThat(unreadyIds).containsExactly(missing, pending, running, failed)
        assertThat(bounded).containsExactly(pending)
        assertThat(targetRepository.count(RelatedGamePrecomputeTargetType.FAILED, upperId)).isEqualTo(2)
        assertThat(targetRepository.count(RelatedGamePrecomputeTargetType.UNREADY, upperId)).isEqualTo(4)
        assertThat(targetRepository.count(RelatedGamePrecomputeTargetType.FAILED, ready)).isEqualTo(1)
    }

    @Test
    fun persistsFinalCountsAndRetainsFailureAfterGameDeletion() {
        // given: 두 게임 중 두 번째 계산만 실패하도록 준비한다.
        val first = createGame()
        val second = createGame()
        val target = target(RelatedGamePrecomputeTargetType.IDS, listOf(first, second))
        val runId = createRun(target)
        doThrow(IllegalStateException("내부 오류 원문")).`when`(precomputeService)
            .precompute(second, RelatedGamePrecomputeMode.REFRESH)

        // when: 실제 MySQL 잠금과 실행 이력 저장을 연결해 계산한다.
        val result = batch.run(target, RelatedGamePrecomputeMode.REFRESH, tracker.observer(runId))

        // then: 순회를 끝내고 집계·실패 상세를 보존하며 내부 오류 원문을 노출하지 않는다.
        val run = runRepository.findById(runId).orElseThrow()
        val failures = failureRepository.findByRun_IdOrderByGameIdAsc(runId, PageRequest.of(0, 20))
        assertThat(result.processed).isEqualTo(2)
        assertThat(run.status).isEqualTo(RelatedGamePrecomputeRunStatus.PARTIAL_FAILED)
        assertThat(run.processed).isEqualTo(2)
        assertThat(run.succeeded).isEqualTo(1)
        assertThat(run.failed).isEqualTo(1)
        assertThat(run.finishedAt).isNotNull()
        assertThat(failures.content).hasSize(1)
        assertThat(failures.content.first().gameId).isEqualTo(second)
        assertThat(failures.content.first().error)
            .contains("IllegalStateException").doesNotContain("내부 오류 원문")
        gameRepository.deleteById(second)
        assertThat(failureRepository.findByRun_IdOrderByGameIdAsc(runId, PageRequest.of(0, 20))
                .totalElements).isEqualTo(1)
    }

    @Test
    fun emptyUnreadyTargetsCompleteSuccessfully() {
        // given: 계산할 게임이 없는 DB에 미완료 복구 실행을 기록한다.
        val target = target(RelatedGamePrecomputeTargetType.UNREADY, emptyList())
        val runId = createRun(target)

        // when: 미완료 복구를 실행한다.
        batch.run(target, RelatedGamePrecomputeMode.REFRESH, tracker.observer(runId))

        // then: 0건을 정상 성공으로 저장하고 계산 서비스는 호출하지 않는다.
        val run = runRepository.findById(runId).orElseThrow()
        assertThat(run.status).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED)
        assertThat(run.estimatedTotal).isZero()
        assertThat(run.processed).isZero()
        verifyNoInteractions(precomputeService)
    }

    @Test
    fun runCreationCommitsIndependentlyFromCallerRollback() {
        // given: 실행 이력을 만든 호출자의 트랜잭션이 실패하도록 준비한다.
        val transaction = TransactionTemplate(transactionManager)
        val target = target(RelatedGamePrecomputeTargetType.ALL, emptyList())

        // when: 이력 생성 이후 호출자 트랜잭션을 롤백한다.
        assertThatThrownBy { transaction.execute {
            createRun(target)
            throw IllegalStateException("호출자 롤백")
        } }.isInstanceOf(IllegalStateException::class.java)

        // then: 독립 트랜잭션으로 커밋한 실행 이력은 유지된다.
        assertThat(runRepository.findById(runIds.first())).isPresent()
    }

    @Test
    fun rollsBackFailureCountTogetherWithRejectedDuplicateDetail() {
        // given: 이미 실패 상세 1건을 저장한 실행을 준비한다.
        val runId = createRun(target(RelatedGamePrecomputeTargetType.IDS, listOf(10L)))
        store.start(runId)
        store.prepared(runId, null, 1)
        store.failure(runId, 10L, progress(1, 0, 1), IllegalStateException("첫 실패"))

        // when: 같은 실행·게임의 중복 상세 저장으로 유일 제약 위반을 유발한다.
        assertThatThrownBy { store.failure(runId, 10L, progress(2, 0, 2),
            IllegalStateException("중복 실패")) }.isInstanceOf(RuntimeException::class.java)

        // then: 상세 저장과 같은 트랜잭션의 집계 증가도 함께 롤백된다.
        val run = runRepository.findById(runId).orElseThrow()
        assertThat(run.processed).isEqualTo(1)
        assertThat(run.failed).isEqualTo(1)
        assertThat(failureRepository.findByRun_IdOrderByGameIdAsc(runId, PageRequest.of(0, 20))
                .totalElements).isEqualTo(1)
    }

    @Test
    fun heartbeatDoesNotReplaceSavedProgressOrAcceptAnotherOwner() {
        // given: 진행 집계를 저장한 관리자 실행을 준비한다.
        val runId = createRun(target(RelatedGamePrecomputeTargetType.ALL, emptyList()))
        store.start(runId)
        store.prepared(runId, 100L, 100L)
        store.progress(runId, progress(10, 10, 0))
        val before = runRepository.findById(runId).orElseThrow()

        // when: 다른 소유자와 현재 소유자의 생존 신호를 차례로 전달한다.
        store.heartbeat(runId, "another-owner")
        val ignored = runRepository.findById(runId).orElseThrow()
        store.heartbeat(runId, before.ownerId)

        // then: 다른 소유자는 무시하고 현재 생존 시각만 갱신하며 집계는 보존한다.
        val after = runRepository.findById(runId).orElseThrow()
        assertThat(ignored.heartbeatAt).isEqualTo(before.heartbeatAt)
        assertThat(after.heartbeatAt).isAfterOrEqualTo(before.heartbeatAt)
        assertThat(after.processed).isEqualTo(10)
        assertThat(after.progressUpdatedAt).isEqualTo(before.progressUpdatedAt)
    }

    @Test
    fun historyFailureDoesNotRollBackCommittedGameCalculation() {
        // given: 실제 계산은 성공하지만 그 직후 진행 통지가 실패하도록 준비한다.
        val first = createGame()
        val second = createGame()
        val target = target(RelatedGamePrecomputeTargetType.IDS, listOf(first, second))
        val runId = createRun(target)
        val delegate = tracker.observer(runId)
        val recordingFailure = IllegalStateException("이력 연결 오류")
        val observer = object : RelatedGamePrecomputeObserver {
            override fun onAcquired() { delegate.onAcquired()
            }
            override fun onPrepared(upperId: Long?, estimatedTotal: Long) {
                delegate.onPrepared(upperId, estimatedTotal)
            }
            override fun onProcessed(gameId: Long, progress: RelatedGamePrecomputeObserver.Progress, failure: RuntimeException?) {
                delegate.onProcessed(gameId, progress, failure)
                throw recordingFailure
            }
            override fun onFailed(failure: RuntimeException) { delegate.onFailed(failure)
            }
        }

        // when: 첫 게임 계산의 커밋 이후 실행 전체를 중단한다.
        assertThatThrownBy { batch.run(target, RelatedGamePrecomputeMode.REFRESH, observer) }
            .isSameAs(recordingFailure)

        // then: 계산 성공 상태는 유지하고 다음 게임은 계산하지 않으며 실행 이력만 실패로 남긴다.
        val calculation = calculationRepository.findById(first).orElseThrow()
        assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.SUCCESS)
        assertThat(calculation.lastSuccessAt).isNotNull()
        assertThat(calculationRepository.findById(second)).isEmpty()
        val run = runRepository.findById(runId).orElseThrow()
        assertThat(run.status).isEqualTo(RelatedGamePrecomputeRunStatus.FAILED)
        assertThat(run.succeeded).isEqualTo(1)
        assertThat(run.failed).isZero()
    }

    @Test
    fun migrationSqlCreatesHistoryTablesUsableByJpa() {
        // given: 이 클래스 전용 MySQL 임시 DB에 이력 데이터가 없음을 확인한다.
        assertThat(runRepository.count()).isZero()
        assertThat(failureRepository.count()).isZero()

        // when: 자동 생성된 이력 테이블만 배포용 DDL로 다시 만든다.
        jdbcTemplate.execute("DROP TABLE related_game_precompute_run_failures")
        jdbcTemplate.execute("DROP TABLE related_game_precompute_runs")
        ResourceDatabasePopulator(FileSystemResource(
            "../docs/sql/add-related-game-precompute-runs.sql")).execute(dataSource)
        val runId = createRun(target(RelatedGamePrecomputeTargetType.IDS, listOf(10L)))
        store.start(runId)
        store.prepared(runId, null, 1)
        store.failure(runId, 10L, progress(1, 0, 1), IllegalStateException("검증용 오류"))
        store.complete(runId, progress(1, 0, 1))

        // then: 배포 DDL에서도 JPA의 모든 실행 필드·외래키·실패 상세 저장이 동작한다.
        val run = runRepository.findById(runId).orElseThrow()
        assertThat(run.status).isEqualTo(RelatedGamePrecomputeRunStatus.PARTIAL_FAILED)
        assertThat(run.failed).isEqualTo(1)
        assertThat(failureRepository.findByRun_IdOrderByGameIdAsc(runId, PageRequest.of(0, 20))
                .totalElements).isEqualTo(1)
    }

    @Test
    fun baselineMigrationPreservesOldRunsAndPersistsRegistrationMarker() {
        // given: 기존 일반 실행을 저장한 뒤 변경 추적 도입 이전 컬럼 구조를 재현한다.
        val oldId = createRun(target(RelatedGamePrecomputeTargetType.ALL, emptyList()))
        jdbcTemplate.execute("ALTER TABLE related_game_precompute_runs DROP COLUMN initialize_baseline, DROP COLUMN baseline_registered_at")

        // when: 기존 DB용 ALTER를 적용하고 초기 기준선 실행의 대기 등록 완료를 기록한다.
        ResourceDatabasePopulator(FileSystemResource(
            "../docs/sql/add-related-game-change-run-options.sql")).execute(dataSource)
        val baselineId = createRun(RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL, emptyList(), true))
        store.start(baselineId)
        store.baselineRegistered(baselineId)

        // then: 과거 실행은 일반 실행으로 유지되고 초기 기준선 옵션·등록 시각은 복원 가능하다.
        assertThat(runRepository.findById(oldId).orElseThrow().initializeBaseline).isFalse()
        val baseline = runRepository.findById(baselineId).orElseThrow()
        assertThat(baseline.target().initializeBaseline).isTrue()
        assertThat(baseline.baselineRegisteredAt).isNotNull()
    }

    private fun target(type: RelatedGamePrecomputeTargetType, ids: List<Long>): RelatedGamePrecomputeTarget {
        return RelatedGamePrecomputeTarget(type, ids)
    }

    private fun progress(processed: Long, succeeded: Long, failed: Long): RelatedGamePrecomputeObserver.Progress {
        return RelatedGamePrecomputeObserver.Progress(processed, succeeded, failed)
    }

    private fun createRun(target: RelatedGamePrecomputeTarget): String {
        val runId = store.create(1L, target, UUID.randomUUID().toString())
        runIds.add(runId)
        return runId
    }

    private fun createGame(): Long {
        val igdbId = IGDB_IDS.incrementAndGet()
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = "Admin precompute $igdbId", summary = null, cover = null,
            first_release_date = 1704067200L, rating = BigDecimal.valueOf(80),
            involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        val gameId = requireNotNull(gameRepository.saveAndFlush(game).id)
        gameIds.add(gameId)
        return gameId
    }

    private fun saveCalculation(gameId: Long, succeededBefore: Boolean, finalStatus: RelatedGameCalculationStatus) {
        TransactionTemplate(transactionManager).execute {
            val game = entityManager.getReference(Game::class.java, gameId)
            val calculation = RelatedGameCalculation(game)
            calculationRepository.save(calculation)
            val now = LocalDateTime.now()
            if (succeededBefore) {
                calculation.start(now)
                calculation.succeed(now)
            }
            if (finalStatus == RelatedGameCalculationStatus.RUNNING) {
                calculation.start(now)
            } else if (finalStatus == RelatedGameCalculationStatus.FAILED) {
                calculation.start(now)
                calculation.fail(now, "이전 계산 실패")
            }
            entityManager.flush()
        }
    }

    companion object {
        private val IGDB_IDS = AtomicLong(2_900_000)
    }
}
