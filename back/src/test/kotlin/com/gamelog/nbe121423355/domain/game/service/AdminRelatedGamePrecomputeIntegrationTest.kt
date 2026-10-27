package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRequest
import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRunResponse
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunFailureRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Duration.ofSeconds
import java.time.LocalDateTime
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileSystemResource
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

@SpringBootTest
@MySqlIntegrationTest
class AdminRelatedGamePrecomputeIntegrationTest {

    @Autowired
    private lateinit var service: AdminRelatedGamePrecomputeService

    @Autowired
    private lateinit var query: RelatedGamePrecomputeRunQueryService

    @Autowired
    private lateinit var recovery: RelatedGamePrecomputeRecoveryService

    @Autowired
    private lateinit var store: RelatedGamePrecomputeRunStore

    @Autowired
    private lateinit var runs: RelatedGamePrecomputeRunRepository

    @Autowired
    private lateinit var failures: RelatedGamePrecomputeRunFailureRepository

    @Autowired
    private lateinit var lock: RelatedGamePrecomputeLock

    @Autowired
    private lateinit var games: GameRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoSpyBean
    private lateinit var batch: RelatedGamePrecomputeBatchService

    @MockitoSpyBean
    private lateinit var calculation: RelatedGamePrecomputeService
    private val runIds = mutableListOf<String>()
    private val gameIds = mutableListOf<Long>()
    private val releases = mutableListOf<CountDownLatch>()

    @AfterEach
    fun cleanUpCommittedFixtures() {
        releases.forEach(CountDownLatch::countDown)
        await().atMost(ofSeconds(5)).until { !service.status().localRequestActive }
        for (id in runIds) {
            jdbc.update("DELETE FROM related_game_precompute_run_failures WHERE run_id = ?", id)
            jdbc.update("DELETE FROM related_game_precompute_runs WHERE id = ?", id)
        }
        gameIds.forEach(games::deleteById)
    }

    @Test
    fun changedAdmissionWorksAfterUpgradingLegacyTargetTypeCheck() {
        // given: 현재 운영 코드와 달리 CHANGED를 허용하지 않는 기존 Hibernate CHECK를 재현한다.
        jdbc.execute("ALTER TABLE related_game_precompute_runs ADD CONSTRAINT ck_legacy_partial_target CHECK (target_type IN ('ALL', 'IDS', 'FAILED', 'UNREADY'))")
        val previousId = manualRun()
        store.start(previousId)
        store.prepared(previousId, null, 0)
        store.complete(previousId, RelatedGamePrecomputeObserver.Progress(0, 0, 0))
        try {

            // when: 기존 DB용 제약 갱신 SQL을 적용하고 실제 관리자 서비스로 변경 기반 실행을 접수한다.
            val populator = ResourceDatabasePopulator(
                FileSystemResource("../docs/sql/update-related-game-precompute-target-types.sql"))
            populator.execute(requireNotNull(jdbc.dataSource))
            val accepted = start(RelatedGamePrecomputeTargetType.CHANGED, null, null)

            // then: DB 제약 409 없이 접수하고 같은 SQL 재적용 후에도 종료 이력과 실행 종류를 유지한다.
            assertThat(accepted.status).isEqualTo(RelatedGamePrecomputeRunStatus.ACCEPTED)
            await().atMost(ofSeconds(5)).untilAsserted { assertThat(query.detail(accepted.runId).status).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED) }
            populator.execute(requireNotNull(jdbc.dataSource))
            assertThat(query.detail(accepted.runId).type).isEqualTo(RelatedGamePrecomputeTargetType.CHANGED)
            assertThat(query.detail(previousId).status).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED)
            // 기존 상태 enum 제약을 제거하지 않았는지도 확인한다.
            assertThatThrownBy { jdbc.update("UPDATE related_game_precompute_runs SET status = 'UNKNOWN' WHERE id = ?", previousId) }
                .isInstanceOf(DataAccessException::class.java)
        } finally {
            // 임시 DB의 기존 제약은 테스트 실패 시에도 다음 테스트에 남기지 않는다.
            val legacyCount = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'related_game_precompute_runs' AND CONSTRAINT_NAME = 'ck_legacy_partial_target'", Long::class.javaObjectType)
            if (legacyCount != null && legacyCount > 0) jdbc.execute("ALTER TABLE related_game_precompute_runs DROP CHECK ck_legacy_partial_target")
        }
    }

    @Test
    fun acceptsWithoutWaitingAndRejectsSecondLocalExecution() {
        // given: 첫 게임 계산을 잠시 멈춰 비동기 실행 상태를 관찰할 수 있게 한다.
        val gameId = createGame()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        releases.add(release)
        doAnswer { invocation ->
            entered.countDown()
            if (!release.await(5, TimeUnit.SECONDS)) throw IllegalStateException("테스트 대기 초과")
            invocation.callRealMethod()
        }.`when`(calculation).precompute(gameId, RelatedGamePrecomputeMode.REFRESH)

        // when: 첫 실행을 접수하고 계산이 끝나기 전에 두 번째 실행을 요청한다.
        val accepted = start(RelatedGamePrecomputeTargetType.IDS, gameId.toString(), null)
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue()
        val conflict = catchThrowable { service.start(7, request(RelatedGamePrecomputeTargetType.ALL, null, null)) }

        // then: 접수 응답은 ACCEPTED이고 실제 worker만 잠금을 보유하며 추가 요청은 409다.
        assertThat(accepted.status).isEqualTo(RelatedGamePrecomputeRunStatus.ACCEPTED)
        assertThat(lock.isLocked()).isTrue()
        assertThat(query.detail(accepted.runId).status).isEqualTo(RelatedGamePrecomputeRunStatus.RUNNING)
        assertThat(conflict).isInstanceOfSatisfying(ServiceException::class.java,
            { e -> assertThat(e.resultCode).isEqualTo("409-10") })
        release.countDown()
        await().atMost(ofSeconds(5)).untilAsserted { assertThat(query.detail(accepted.runId).status).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED) }
    }

    @Test
    fun recordsRejectedWhenAnotherProcessWinsLockAfterAdmission() {
        // given: 접수와 실제 배치 잠금 획득 사이를 테스트에서 제어한다.
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        releases.add(release)
        doAnswer { invocation ->
            entered.countDown()
            if (!release.await(5, TimeUnit.SECONDS)) throw IllegalStateException("테스트 대기 초과")
            invocation.callRealMethod()
        }.`when`(batch).run(anyTarget(), refreshMode(), anyObserver())

        // when: 접수 후 다른 MySQL 세션이 실행 잠금을 먼저 획득한다.
        val accepted = start(RelatedGamePrecomputeTargetType.UNREADY, null, null)
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue()
        lock.tryRun({ check ->
            release.countDown()
            await().atMost(ofSeconds(5)).untilAsserted { assertThat(query.detail(accepted.runId).status).isEqualTo(RelatedGamePrecomputeRunStatus.REJECTED) }
            true
        })

        // then: 계산 0건의 실행 거절을 기록하고 자동으로 기다렸다 실행하지 않는다.
        val rejected = query.detail(accepted.runId)
        assertThat(rejected.processed).isZero()
        assertThat(rejected.finishedAt).isNotNull()
        verifyNoInteractions(calculation)
    }

    @Test
    fun recoveryDefersWhileLockIsHeldAndDoesNotResumeCalculation() {
        // given: 오래된 RUNNING 이력과 아직 살아 있는 ACCEPTED 이력을 준비한다.
        val staleId = manualRun()
        store.start(staleId)
        store.prepared(staleId, 10L, 10)
        makeStale(staleId)
        val freshId = manualRun()

        // when: 다른 실행의 잠금 보유 중 복구를 시도하고 잠금 해제 후 다시 점검한다.
        val deferred = lock.tryRun({ check ->
            assertThat(recovery.recover()).isZero()
            assertThat(query.detail(staleId).status).isEqualTo(RelatedGamePrecomputeRunStatus.RUNNING)
            assertThat(service.status().recoveryDeferred).isTrue()
            0
        })
        val recovered = recovery.recover()

        // then: 실제 잠금이 없어진 뒤 오래된 이력만 중단으로 정리하고 계산은 재개하지 않는다.
        assertThat(deferred).isZero()
        assertThat(recovered).isEqualTo(1)
        assertThat(query.detail(staleId).status).isEqualTo(RelatedGamePrecomputeRunStatus.INTERRUPTED)
        assertThat(query.detail(freshId).status).isEqualTo(RelatedGamePrecomputeRunStatus.ACCEPTED)
        verifyNoInteractions(calculation)
    }

    @Test
    fun interruptionRechecksHeartbeatAfterCandidateSelection() {
        // given: 복구 대상으로 선정된 이후 생존 신호가 갱신된 실행을 준비한다.
        val runId = manualRun()
        makeStale(runId)
        val cutoff = LocalDateTime.now().minusMinutes(1)
        val owner = runs.findById(runId).orElseThrow().ownerId
        store.heartbeat(runId, owner)

        // when: 공통 잠금을 획득한 복구 구간에서 이전 기준 시각으로 중단 처리를 시도한다.
        val interrupted = lock.tryRun({ check -> store.interruptStale(runId, cutoff) })

        // then: 행을 다시 읽어 갱신된 생존 신호를 확인하고 중단하지 않는다.
        assertThat(interrupted).isFalse()
        assertThat(query.detail(runId).status).isEqualTo(RelatedGamePrecomputeRunStatus.ACCEPTED)
    }

    @Test
    fun replayCreatesNewRunAndPreservesOriginalResult() {
        // given: 지정 게임의 첫 갱신이 정상 종료된 상태를 준비한다.
        val gameId = createGame()
        val first = start(RelatedGamePrecomputeTargetType.IDS, gameId.toString(), null)
        await().atMost(ofSeconds(5)).untilAsserted { assertThat(query.detail(first.runId).status).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED) }
        await().atMost(ofSeconds(5)).until { !service.status().localRequestActive }

        // when: 원본 실행 ID를 연결하여 같은 종류·게임으로 다시 요청한다.
        val replay = start(RelatedGamePrecomputeTargetType.IDS, gameId.toString(), first.runId)
        await().atMost(ofSeconds(5)).untilAsserted { assertThat(query.detail(replay.runId).status).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED) }

        // then: 원본을 덮어쓰지 않고 새 실행에 재요청 연결 정보를 저장한다.
        assertThat(replay.runId).isNotEqualTo(first.runId)
        assertThat(query.detail(replay.runId).sourceRunId).isEqualTo(first.runId)
        assertThat(query.detail(first.runId).status).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED)
    }

    @Test
    fun historyAndFailuresArePagedAndInternalOwnerIsNotExposed() {
        // given: 두 실행과 실패 상세 2개를 준비한다.
        val failedId = manualRun()
        store.start(failedId)
        store.prepared(failedId, null, 2)
        store.failure(failedId, 20L, RelatedGamePrecomputeObserver.Progress(1, 0, 1), IllegalStateException("원문"))
        store.failure(failedId, 10L, RelatedGamePrecomputeObserver.Progress(2, 0, 2), IllegalStateException("원문"))
        store.complete(failedId, RelatedGamePrecomputeObserver.Progress(2, 0, 2))
        manualRun()

        // when: 한 행씩 이력과 실패 상세를 조회하고 잘못된 페이지도 요청한다.
        val history = query.history(0, 1)
        val detail = query.failures(failedId, 1, 1)
        val invalidPage = catchThrowable { query.history(-1, 20) }
        val missing = catchThrowable { query.detail("missing") }

        // then: 전체 개수·안정적인 정렬과 오류 코드를 반환하며 엔티티를 직접 노출하지 않는다.
        assertThat(history.content).hasSize(1)
        assertThat(history.totalElements).isEqualTo(2)
        assertThat(detail.content.first().gameId).isEqualTo(20)
        assertThat(detail.totalElements).isEqualTo(2)
        assertThat(invalidPage).isInstanceOfSatisfying(ServiceException::class.java,
            { e -> assertThat(e.resultCode).isEqualTo("400-10") })
        assertThat(missing).isInstanceOfSatisfying(ServiceException::class.java,
            { e -> assertThat(e.resultCode).isEqualTo("404-10") })
    }

    private fun request(type: RelatedGamePrecomputeTargetType, ids: String?, source: String?): AdminRelatedGamePrecomputeRequest =
    AdminRelatedGamePrecomputeRequest(type = type, gameIds = ids, sourceRunId = source)

    private fun start(type: RelatedGamePrecomputeTargetType, ids: String?, source: String?): AdminRelatedGamePrecomputeRunResponse {
        val response = service.start(7, request(type, ids, source))
        runIds.add(response.runId)
        return response
    }

    private fun manualRun(): String {
        val id = store.create(7, RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL, emptyList()), "other-owner")
        runIds.add(id)
        return id
    }

    private fun makeStale(runId: String) {
        jdbc.update("UPDATE related_game_precompute_runs SET heartbeat_at = ? WHERE id = ?",
            Timestamp.valueOf(LocalDateTime.now().minusMinutes(2)), runId)
    }

    private fun createGame(): Long {
        val igdbId = IGDB_IDS.incrementAndGet()
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = "Admin API $igdbId", summary = null, cover = null,
            first_release_date = 1704067200L, rating = BigDecimal.valueOf(80),
            involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        val id = requireNotNull(games.saveAndFlush(game).id)
        gameIds.add(id)
        return id
    }
    // Mockito는 매처 등록 시 null을 반환하므로 Kotlin 필수 인자에 대체값을 제공한다.

    private fun anyTarget(): RelatedGamePrecomputeTarget =
    any<RelatedGamePrecomputeTarget>() ?: RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL)

    private fun anyObserver(): RelatedGamePrecomputeObserver =
    any<RelatedGamePrecomputeObserver>() ?: RelatedGamePrecomputeObserver.NONE

    private fun refreshMode(): RelatedGamePrecomputeMode =
    eq(RelatedGamePrecomputeMode.REFRESH) ?: RelatedGamePrecomputeMode.REFRESH

    companion object {
        private val IGDB_IDS = AtomicLong(3_900_000)
    }
}
