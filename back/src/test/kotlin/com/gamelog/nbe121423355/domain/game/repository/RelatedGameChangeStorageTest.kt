package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.GAME_ADDED
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.GAME_DELETED
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.GAME_INFORMATION
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.INITIAL_BASELINE
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.READY
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.REVIEW
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.USER_GAME_RECORD
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameInputChange
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameRefreshTarget
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.FileSystemResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

// 배포 DDL 재생성 검증용 컨텍스트와 임시 DB를 다른 테스트에서 분리한다.
@SpringBootTest(properties = ["test.database-isolation=related-game-change-storage"])
@MySqlIntegrationTest
class RelatedGameChangeStorageTest {

    @Autowired
    private lateinit var changes: RelatedGameInputChangeRepository

    @Autowired
    private lateinit var targets: RelatedGameRefreshTargetRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource
    private val gameIds = mutableListOf<Long>()
    private val sourceIds = mutableListOf<Long>()

    @AfterEach
    fun cleanUpCommittedFixtures() {
        for (gameId in sourceIds) {
            jdbcTemplate.update("DELETE FROM related_game_input_changes WHERE game_id = ?", gameId)
        }
        for (gameId in gameIds) {
            jdbcTemplate.update("DELETE FROM games WHERE id = ?", gameId)
        }
    }

    @Test
    fun mergesGenresAndReasonsWithoutLosingIntermediateChanges() {
        // given: 첫 변경에 중복 장르가 있고 다음 변경의 시각은 더 오래된 원천을 준비한다.
        val sourceId = newSourceId()
        inTransaction { changes.record(sourceId, listOf(3L, 1L, 3L), setOf(GAME_INFORMATION), CHANGED_AT) }

        // when: 같은 게임의 중간 장르와 리뷰 변경을 추가로 기록한다.
        val version = inTransaction { changes.record(sourceId, listOf(2L, 3L),
            setOf(REVIEW), CHANGED_AT.minusMinutes(1)) }

        // then: 버전을 증가시키고 장르 합집합·모든 이유·가장 늦은 변경 시각을 보존한다.
        val change = requireNotNull(changes.findByGameId(sourceId))
        assertThat(version).isEqualTo(2)
        assertThat(change.changeVersion).isEqualTo(2)
        assertThat(change.expandedVersion).isZero()
        assertThat(change.affectedGenreIds).containsExactly(1L, 2L, 3L)
        assertThat(change.reasonMask).isEqualTo(GAME_INFORMATION.mask or REVIEW.mask)
        assertThat(change.changedAt).isEqualTo(CHANGED_AT)
        assertThat(change.isPending).isTrue()
    }

    @Test
    fun rollsBackOriginalGameAndChangeRecordTogether() {
        // given: 게임 생성과 변경 기록을 함께 저장할 호출자 트랜잭션을 준비한다.
        val failure = IllegalStateException("원본 저장 실패")

        // when: 원본 게임과 변경 기록을 저장한 뒤 호출자 작업을 실패시킨다.
        assertThatThrownBy { inTransaction {
            val gameId = createGame()
            sourceIds.add(gameId)
            changes.record(gameId, listOf(1L), setOf(GAME_ADDED), CHANGED_AT)
            throw failure
        } }.isSameAs(failure)

        // then: 독립 커밋 없이 원본 게임과 변경 원천이 모두 롤백된다.
        assertThat(gameRepository.existsById(gameIds.first())).isFalse()
        assertThat(changes.findByGameId(sourceIds.first())).isNull()
    }

    @Test
    fun requiresCallerTransactionForAllMutations() {
        // given: 트랜잭션 없이 저장소를 호출할 양수 ID를 준비한다.
        val sourceId = newSourceId()

        // when: 원천 기록·확장 확인·대기 등록·반영 확인을 트랜잭션 밖에서 호출한다.
        val failures = listOf<() -> Any?>(
            { changes.record(sourceId, emptyList(), setOf(REVIEW), CHANGED_AT) },
            { changes.markExpanded(sourceId, 1) },
            { targets.request(sourceId, setOf(REVIEW), CHANGED_AT) },
            { targets.markApplied(sourceId, 1) })

        // then: 모든 쓰기를 거절하고 원천이나 대기 행을 자동 커밋하지 않는다.
        for (mutation in failures) {
            assertThatThrownBy { mutation() }.isInstanceOf(IllegalTransactionStateException::class.java)
        }
        assertThat(changes.findByGameId(sourceId)).isNull()
        assertThat(targets.findByGameId(sourceId)).isNull()
    }

    @Test
    fun serializesConcurrentFirstRecordsWithoutLosingEitherChange() {
        // given: 동일한 신규 원천을 동시에 생성할 두 트랜잭션과 시작 신호를 준비한다.
        val sourceId = newSourceId()
        val start = CountDownLatch(1)

        // when: 서로 다른 장르·이유를 가진 변경을 별도 스레드에서 함께 기록한다.
        val versions = Executors.newFixedThreadPool(2).use { executor ->
            val first = executor.submit<Long> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue()
                requireNotNull(inTransaction { changes.record(sourceId, listOf(1L), setOf(REVIEW), CHANGED_AT) })
            }
            val second = executor.submit<Long> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue()
                requireNotNull(inTransaction { changes.record(sourceId, listOf(2L), setOf(USER_GAME_RECORD), CHANGED_AT) })
            }
            start.countDown()
            listOf(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
        }

        // then: 신규 행 경합에도 두 변경을 한 행에 병합하고 각각의 버전을 반영한다.
        assertThat(versions).containsExactlyInAnyOrder(1L, 2L)
        val change = requireNotNull(changes.findByGameId(sourceId))
        assertThat(change.changeVersion).isEqualTo(2)
        assertThat(change.affectedGenreIds).containsExactly(1L, 2L)
        assertThat(change.reasonMask).isEqualTo(REVIEW.mask or USER_GAME_RECORD.mask)
    }

    @Test
    fun mergesLatestLockedStateEvenWhenCallerHasAnOlderReadSnapshot() {
        // given: 첫 원천을 저장하고 호출자 읽기 스냅샷 이후 다른 트랜잭션이 새 변경을 커밋한다.
        val sourceId = newSourceId()
        inTransaction { changes.record(sourceId, listOf(1L), setOf(GAME_INFORMATION), CHANGED_AT) }

        // when: 오래된 스냅샷을 가진 호출자가 세 번째 변경을 기록한다.
        val version = inTransaction {
            assertThat(requireNotNull(changes.findByGameId(sourceId)).changeVersion).isEqualTo(1)
            inTransaction { changes.record(sourceId, listOf(2L), setOf(REVIEW), CHANGED_AT) }
            changes.record(sourceId, listOf(3L), setOf(USER_GAME_RECORD), CHANGED_AT)
        }

        // then: 현재 읽기 행 잠금으로 중간 커밋까지 병합하여 버전과 장르를 잃지 않는다.
        assertThat(version).isEqualTo(3)
        val change = requireNotNull(changes.findByGameId(sourceId))
        assertThat(change.affectedGenreIds).containsExactly(1L, 2L, 3L)
        assertThat(change.reasonMask).isEqualTo(
            GAME_INFORMATION.mask or REVIEW.mask or USER_GAME_RECORD.mask)
    }

    @Test
    fun acknowledgesOnlyCapturedSourceVersionAndKeepsLaterChanges() {
        // given: 버전 1을 캡처한 뒤 버전 2의 장르·이유 변경이 들어온 원천을 준비한다.
        val sourceId = newSourceId()
        val captured = inTransaction { changes.record(sourceId, listOf(1L), setOf(REVIEW), CHANGED_AT) }
        inTransaction { changes.record(sourceId, listOf(2L), setOf(GAME_INFORMATION), CHANGED_AT) }

        // when: 캡처한 버전까지만 확장 완료를 표시한다.
        val advanced = inTransaction { changes.markExpanded(sourceId, requireNotNull(captured)) }

        // then: 새 버전은 미확장으로 남고 장르·이유 합집합을 성급하게 비우지 않는다.
        val change = requireNotNull(changes.findByGameId(sourceId))
        assertThat(advanced).isTrue()
        assertThat(change.expandedVersion).isEqualTo(1)
        assertThat(change.changeVersion).isEqualTo(2)
        assertThat(change.isPending).isTrue()
        assertThat(change.affectedGenreIds).containsExactly(1L, 2L)
        assertThat(change.reasonMask).isEqualTo(REVIEW.mask or GAME_INFORMATION.mask)
    }

    @Test
    fun clearsCompletedSourcePayloadAndNeverResetsItsVersion() {
        // given: 최신 버전까지 확장 완료한 원천을 준비한다.
        val sourceId = newSourceId()
        inTransaction { changes.record(sourceId, listOf(1L), setOf(REVIEW), CHANGED_AT) }
        inTransaction { changes.markExpanded(sourceId, 1) }
        val completed = requireNotNull(changes.findByGameId(sourceId))

        // when: 완료 후 새 변경을 기록하고 이전 버전 완료 통지가 다시 도착한다.
        val version = inTransaction { changes.record(sourceId, listOf(2L), setOf(READY), CHANGED_AT) }
        val staleAcknowledgement = inTransaction { changes.markExpanded(sourceId, 1) }

        // then: 완료한 정보만 비우고 새 버전·새 장르·새 이유를 유지한다.
        assertThat(completed.isPending).isFalse()
        assertThat(completed.affectedGenreIds).isEmpty()
        assertThat(completed.reasonMask).isZero()
        assertThat(version).isEqualTo(2)
        assertThat(staleAcknowledgement).isFalse()
        val pending = requireNotNull(changes.findByGameId(sourceId))
        assertThat(pending.expandedVersion).isEqualTo(1)
        assertThat(pending.affectedGenreIds).containsExactly(2L)
        assertThat(pending.reasonMask).isEqualTo(READY.mask)
    }

    @Test
    fun rejectsFutureSourceAcknowledgementWithoutChangingState() {
        // given: 실제로 기록된 버전은 1인 원천을 준비한다.
        val sourceId = newSourceId()
        inTransaction { changes.record(sourceId, listOf(1L), setOf(REVIEW), CHANGED_AT) }

        // when: 존재하지 않는 버전 2까지 확장 완료 표시를 시도한다.
        assertThatThrownBy { inTransaction { changes.markExpanded(sourceId, 2) } }
            .hasRootCauseInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("기록된 변경 버전을 넘을 수 없습니다.")

        // then: 아직 미처리인 장르·이유와 확장 완료 버전을 보존한다.
        val pending = requireNotNull(changes.findByGameId(sourceId))
        assertThat(pending.expandedVersion).isZero()
        assertThat(pending.affectedGenreIds).containsExactly(1L)
        assertThat(pending.reasonMask).isEqualTo(REVIEW.mask)
    }

    @Test
    fun mergesTargetRequestsAndAcknowledgesOnlyCapturedVersion() {
        // given: 같은 기준 게임에 서로 다른 이유의 갱신 요구가 두 번 들어온다.
        val gameId = createGame()
        val captured = inTransaction { targets.request(gameId, setOf(REVIEW), CHANGED_AT) }
        val latest = inTransaction { targets.request(gameId, setOf(GAME_INFORMATION), CHANGED_AT) }

        // when: 먼저 캡처한 요구 버전까지만 반영 완료 처리한다.
        val advanced = inTransaction { targets.markApplied(gameId, requireNotNull(captured)) }

        // then: 요구를 한 행에 병합하고 새 버전과 선정 이유를 대기로 유지한다.
        val pending = requireNotNull(targets.findByGameId(gameId))
        assertThat(captured).isEqualTo(1)
        assertThat(latest).isEqualTo(2)
        assertThat(advanced).isTrue()
        assertThat(pending.requiredVersion).isEqualTo(2)
        assertThat(pending.appliedVersion).isEqualTo(1)
        assertThat(pending.isPending).isTrue()
        assertThat(pending.reasonMask).isEqualTo(REVIEW.mask or GAME_INFORMATION.mask)
    }

    @Test
    fun keepsMonotonicTargetVersionsAcrossCompletionAndRepeatedBaselineRequests() {
        // given: 초기 기준선 요구와 후속 요구를 모두 반영한 기준 게임을 준비한다.
        val gameId = createGame()
        inTransaction { targets.request(gameId, setOf(INITIAL_BASELINE), CHANGED_AT) }
        inTransaction { targets.request(gameId, setOf(REVIEW), CHANGED_AT) }
        inTransaction { targets.markApplied(gameId, 2) }
        val completed = requireNotNull(targets.findByGameId(gameId))

        // when: 초기 기준선을 재요청하고 더 오래된 완료 통지를 전달한다.
        val next = inTransaction { targets.request(gameId, setOf(INITIAL_BASELINE), CHANGED_AT.minusMinutes(1)) }
        val stale = inTransaction { targets.markApplied(gameId, 1) }

        // then: 요구 버전을 초기화하지 않고 새 대기와 마지막 변경 시각을 유지한다.
        assertThat(completed.isPending).isFalse()
        assertThat(completed.reasonMask).isZero()
        assertThat(next).isEqualTo(3)
        assertThat(stale).isFalse()
        val pending = requireNotNull(targets.findByGameId(gameId))
        assertThat(pending.appliedVersion).isEqualTo(2)
        assertThat(pending.reasonMask).isEqualTo(INITIAL_BASELINE.mask)
        assertThat(pending.changedAt).isEqualTo(CHANGED_AT)
    }

    @Test
    fun rejectsFutureTargetAcknowledgementAndRollsBackCompletionWithCaller() {
        // given: 아직 반영되지 않은 기준 게임의 요구 버전 1을 준비한다.
        val gameId = createGame()
        inTransaction { targets.request(gameId, setOf(REVIEW), CHANGED_AT) }

        // when: 미래 버전 완료와, 정상 완료 표시 후 호출자 실패를 각각 시도한다.
        assertThatThrownBy { inTransaction { targets.markApplied(gameId, 2) } }
            .hasRootCauseInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("갱신 필요 버전을 넘을 수 없습니다.")
        assertThatThrownBy { inTransaction {
            targets.markApplied(gameId, 1)
            throw IllegalStateException("후보 교체 트랜잭션 실패")
        } }.isInstanceOf(IllegalStateException::class.java)

        // then: 잘못된 확인이나 호출자 롤백으로 대기를 지우지 않는다.
        val pending = requireNotNull(targets.findByGameId(gameId))
        assertThat(pending.appliedVersion).isZero()
        assertThat(pending.isPending).isTrue()
        assertThat(pending.reasonMask).isEqualTo(REVIEW.mask)
    }

    @Test
    fun rollsBackTargetRequestAndSourceAcknowledgementTogether() {
        // given: 아직 확장하지 않은 원천과 영향받는 기준 게임을 준비한다.
        val sourceId = newSourceId()
        val gameId = createGame()
        inTransaction { changes.record(sourceId, listOf(1L), setOf(REVIEW), CHANGED_AT) }

        // when: 대상 등록과 원천 완료 표시를 같은 트랜잭션에서 수행한 뒤 실패시킨다.
        assertThatThrownBy { inTransaction {
            targets.request(gameId, setOf(REVIEW), CHANGED_AT)
            changes.markExpanded(sourceId, 1)
            throw IllegalStateException("영향 대상 저장 실패")
        } }.isInstanceOf(IllegalStateException::class.java)

        // then: 대상과 원천 확인이 함께 롤백되어 다음 실행에서 원천을 다시 찾을 수 있다.
        assertThat(targets.findByGameId(gameId)).isNull()
        assertThat(requireNotNull(changes.findByGameId(sourceId)).expandedVersion).isZero()
        assertThat(requireNotNull(changes.findByGameId(sourceId)).affectedGenreIds).containsExactly(1L)
    }

    @Test
    fun preservesDeletedGameSourceAndCascadesItsRefreshTarget() {
        // given: 같은 게임의 삭제 이전 장르와 갱신 대기를 기록한다.
        val gameId = createGame()
        sourceIds.add(gameId)
        inTransaction { changes.record(gameId, listOf(1L, 2L), setOf(GAME_DELETED), CHANGED_AT) }
        inTransaction { targets.request(gameId, setOf(REVIEW), CHANGED_AT) }

        // when: IGDB 삭제처럼 JPA 훅 없이 게임을 지우고 늦은 갱신 요청·완료를 전달한다.
        jdbcTemplate.update("DELETE FROM games WHERE id = ?", gameId)
        val requested = inTransaction { targets.request(gameId, setOf(REVIEW), CHANGED_AT) }
        val applied = inTransaction { targets.markApplied(gameId, 1) }

        // then: 삭제 전 정보는 남지만 삭제한 기준 게임의 대기는 되살리지 않는다.
        assertThat(requireNotNull(changes.findByGameId(gameId)).affectedGenreIds).containsExactly(1L, 2L)
        assertThat(targets.findByGameId(gameId)).isNull()
        assertThat(requested).isNull()
        assertThat(applied).isFalse()
    }

    @Test
    fun deploymentSqlSupportsJdbcWritesJpaReadsAndDatabaseConstraints() {
        // given: 이 클래스에서 사용하는 격리 MySQL에 변경·대기 데이터가 없음을 확인한다.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM related_game_input_changes", Long::class.javaObjectType)).isZero()
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM related_game_refresh_targets", Long::class.javaObjectType)).isZero()

        // when: 두 테이블만 배포 SQL로 다시 만든 뒤 JDBC로 저장하고 JPA로 읽는다.
        jdbcTemplate.execute("DROP TABLE related_game_refresh_targets")
        jdbcTemplate.execute("DROP TABLE related_game_input_changes")
        ResourceDatabasePopulator(FileSystemResource(
            "../docs/sql/add-related-game-change-tracking.sql")).execute(dataSource)
        val gameId = createGame()
        sourceIds.add(gameId)
        inTransaction {
            changes.record(gameId, listOf(1L, 2L), setOf(REVIEW), CHANGED_AT)
            targets.request(gameId, setOf(REVIEW), CHANGED_AT)
        }
        val mappedChange = requireNotNull(inTransaction { entityManager.find(RelatedGameInputChange::class.java, gameId) })
        val mappedTarget = requireNotNull(inTransaction { entityManager.find(RelatedGameRefreshTarget::class.java, gameId) })

        // then: 배포 DDL의 JSON·버전·연결 매핑이 맞고 미래 완료 버전은 DB에서도 거절한다.
        assertThat(mappedChange.affectedGenreIds).containsExactly(1L, 2L)
        assertThat(mappedChange.changeVersion).isEqualTo(1)
        assertThat(mappedChange.changedAt).isEqualTo(CHANGED_AT)
        assertThat(mappedTarget.requiredVersion).isEqualTo(1)
        assertThat(mappedTarget.changedAt).isEqualTo(CHANGED_AT)
        assertThat(mappedTarget.game.id).isEqualTo(gameId)
        assertThatThrownBy { jdbcTemplate.update(
            "UPDATE related_game_input_changes SET expanded_version = 2 WHERE game_id = ?", gameId) }
            .rootCause().isInstanceOfSatisfying(SQLException::class.java,
                { failure -> assertThat(failure.errorCode).isEqualTo(3819) })
        assertThatThrownBy { jdbcTemplate.update(
            "UPDATE related_game_refresh_targets SET applied_version = 2 WHERE game_id = ?", gameId) }
            .rootCause().isInstanceOfSatisfying(SQLException::class.java,
                { failure -> assertThat(failure.errorCode).isEqualTo(3819) })
        assertThatThrownBy { jdbcTemplate.update(
            "INSERT INTO related_game_refresh_targets VALUES (?, 1, 0, 1, ?)", newSourceId(), CHANGED_AT) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun <T> inTransaction(work: () -> T): T? {
        val transaction = TransactionTemplate(transactionManager)
        transaction.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        return transaction.execute { work() }
    }

    private fun newSourceId(): Long {
        val sourceId = SOURCE_IDS.incrementAndGet()
        sourceIds.add(sourceId)
        return sourceId
    }

    private fun createGame(): Long {
        val igdbId = IGDB_IDS.incrementAndGet()
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = "Change storage $igdbId", summary = null, cover = null,
            first_release_date = 1704067200L, rating = BigDecimal.valueOf(80),
            involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        val gameId = requireNotNull(gameRepository.saveAndFlush(game).id)
        gameIds.add(gameId)
        return gameId
    }

    companion object {
        private val IGDB_IDS = AtomicLong(3_500_000)
        private val SOURCE_IDS = AtomicLong(9_000_000_000L)
        private val CHANGED_AT = LocalDateTime.of(2026, 10, 23, 16, 0)
    }
}
