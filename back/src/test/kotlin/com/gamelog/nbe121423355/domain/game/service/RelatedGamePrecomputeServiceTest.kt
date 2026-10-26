package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculationStatus
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidate
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidateId
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCandidateRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlTestConfiguration
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.sql.SQLIntegrityConstraintViolationException
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@Import(MySqlTestConfiguration::class)
@ActiveProfiles("test")
class RelatedGamePrecomputeServiceTest {
    @Autowired
    private lateinit var service: RelatedGamePrecomputeService
    @Autowired
    private lateinit var store: RelatedGamePrecomputeStore
    @MockitoSpyBean
    private lateinit var queryRepository: RelatedGameQueryRepository
    @Autowired
    private lateinit var candidateRepository: RelatedGameCandidateRepository
    @Autowired
    private lateinit var calculationRepository: RelatedGameCalculationRepository
    @Autowired
    private lateinit var entityManager: EntityManager
    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val gameIds = mutableListOf<Long>()
    private val genreIds = mutableListOf<Long>()
    private val previousSuccess = LocalDateTime.of(2026, 10, 1, 10, 0)

    @AfterEach
    fun cleanUpCommittedFixtures() {
        inTransaction {
            if (gameIds.isNotEmpty()) {
                entityManager.createQuery("DELETE FROM GameGenre link WHERE link.game.id IN :ids")
                    .setParameter("ids", gameIds).executeUpdate()
                entityManager.createQuery("DELETE FROM Game game WHERE game.id IN :ids")
                    .setParameter("ids", gameIds).executeUpdate()
            }
            if (genreIds.isNotEmpty()) {
                entityManager.createQuery("DELETE FROM Genre genre WHERE genre.id IN :ids")
                    .setParameter("ids", genreIds).executeUpdate()
            }
        }
    }

    @Test
    fun initialLoadStoresTopTwentyAndPreservesExistingTopFiveScoresAndOrder() {
        // given: 후보 25개와 기존 실시간 추천 상위 5개 결과를 준비한다.
        val fixture = createFixture(25, 0)
        val existingTopFive = inTransaction { queryRepository.findRelatedGames(fixture.sourceId) }

        // when: 최초 적재 모드로 기준 게임을 계산한다.
        val count = service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.INITIAL_LOAD)

        // then: 상위 20개를 저장하고 기존 상위 5개의 점수·순서와 성공 상태를 유지한다.
        assertThat(count).isEqualTo(20)
        val saved = storedCandidates(fixture.sourceId)
        assertThat(saved).hasSize(20)
        assertThat(saved.take(5).map { it.gameId }).containsExactlyElementsOf(existingTopFive.map { it.id })
        for (index in 0 until 5) {
            assertThat(saved[index].relatedScore).isEqualByComparingTo(existingTopFive[index].recommendationScore)
        }
        inTransaction {
            assertThat(candidateRepository.findByIdSourceGameIdOrderByRelatedRankAsc(fixture.sourceId).map { it.relatedRank })
                .containsExactlyElementsOf((1..20).toList())
            val calculation = calculationRepository.findById(fixture.sourceId).orElseThrow()
            assertThat(calculation.isReady).isTrue()
            assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.SUCCESS)
        }
    }

    @Test
    fun refreshUsesOnlyReadyCandidatesButInitialLoadDoesNot() {
        // given: 후보 세 개 중 하나만 준비 완료인 기준 게임을 준비한다.
        val fixture = createFixture(3, 1)

        // when: 준비 조건이 있는 갱신과 조건 없는 최초 적재를 순서대로 실행한다.
        val refreshCount = service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.REFRESH)
        val refreshed = storedCandidates(fixture.sourceId)
        val initialCount = service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.INITIAL_LOAD)
        val initial = storedCandidates(fixture.sourceId)

        // then: 갱신에서는 준비된 한 후보만 저장하고 최초 적재에서는 세 후보를 저장한다.
        assertThat(refreshCount).isEqualTo(1)
        assertThat(refreshed.map { it.gameId }).containsExactly(fixture.candidateIds.first())
        assertThat(initialCount).isEqualTo(3)
        assertThat(initial).hasSize(3)
    }

    @Test
    fun sharedBaselineIsSavedWithoutExecutingPerSourceRecommendationQuery() {
        // given: 사용자 행동이 없는 기준 게임과 공통 스냅샷을 준비한다.
        val fixture = createFixture(3, 0)
        val snapshot = service.prepare(RelatedGamePrecomputeMode.INITIAL_LOAD, fixture.candidateIds.last())

        // when: 공통 스냅샷으로 기준 게임을 계산한다.
        val count = service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.INITIAL_LOAD, snapshot)

        // then: 개별 SQL 없이 공통 순위대로 세 후보를 저장한다.
        assertThat(count).isEqualTo(3)
        verify(queryRepository, never()).findCandidatesForPrecompute(fixture.sourceId, 20, false)
        assertThat(storedCandidates(fixture.sourceId).map { it.gameId })
            .containsExactly(fixture.candidateIds[2], fixture.candidateIds[1], fixture.candidateIds.first())
    }

    @Test
    fun sharedEmptyBaselineIsSuccessfulWithoutFallingBackToSql() {
        // given: 이전 정상 후보가 있는 게임에 장르가 없는 공통 스냅샷을 준비한다.
        val fixture = createFixture(1, 1)
        seedPreviousResult(fixture)
        val snapshot = RelatedGamePrecomputeSnapshot(
            RelatedGamePrecomputeMode.REFRESH, 20, mapOf(fixture.sourceId to emptyList()), emptyMap(), emptySet(),
        )

        // when: 정상 빈 결과를 제공하는 공통 스냅샷으로 갱신한다.
        val count = service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.REFRESH, snapshot)

        // then: SQL로 전환하지 않고 이전 후보를 비우며 준비 완료와 성공 상태를 유지한다.
        assertThat(count).isZero()
        verify(queryRepository, never()).findCandidatesForPrecompute(fixture.sourceId, 20, true)
        assertThat(storedCandidates(fixture.sourceId)).isEmpty()
        val calculation = calculationRepository.findById(fixture.sourceId).orElseThrow()
        assertThat(calculation.isReady).isTrue()
        assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.SUCCESS)
    }

    @Test
    fun interactionSourceStillUsesExistingQueryAndAtomicStore() {
        // given: 사용자 행동이 있어 개별 SQL을 요구하는 기준 게임의 스냅샷을 준비한다.
        val fixture = createFixture(3, 0)
        val snapshot = RelatedGamePrecomputeSnapshot(
            RelatedGamePrecomputeMode.INITIAL_LOAD, 20,
            mapOf(fixture.sourceId to listOf(1L)), emptyMap(), setOf(fixture.sourceId),
        )

        // when: 행동 기준 게임을 공통 스냅샷과 함께 계산한다.
        val count = service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.INITIAL_LOAD, snapshot)

        // then: 기존 SQL 계산과 저장 경로로 세 후보를 반영한다.
        assertThat(count).isEqualTo(3)
        verify(queryRepository).findCandidatesForPrecompute(fixture.sourceId, 20, false)
        assertThat(storedCandidates(fixture.sourceId)).hasSize(3)
    }

    @Test
    fun replacesExistingPairsAndRanksWithoutLeavingOldScores() {
        // given: 준비된 후보 세 개와 과거 점수의 정상 저장 결과를 준비한다.
        val fixture = createFixture(3, 3)
        seedPreviousResult(fixture)

        // when: 기준 게임을 갱신한다.
        val count = service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.REFRESH)

        // then: 과거 점수를 남기지 않고 순위·점수·성공 시각을 교체한다.
        assertThat(count).isEqualTo(3)
        val saved = storedCandidates(fixture.sourceId)
        assertThat(saved.map { it.gameId })
            .containsExactly(fixture.candidateIds[2], fixture.candidateIds[1], fixture.candidateIds.first())
        assertThat(saved).allSatisfy { candidate -> assertThat(candidate.relatedScore).isGreaterThan(BigDecimal("50")) }
        assertThat(calculationRepository.findById(fixture.sourceId).orElseThrow().lastSuccessAt).isAfter(previousSuccess)
    }

    @Test
    fun successfulEmptyResultRemovesOldCandidatesAndStaysReady() {
        // given: 과거 정상 후보를 저장한 뒤 기준 게임의 장르 연결을 제거한다.
        val fixture = createFixture(1, 1)
        seedPreviousResult(fixture)
        inTransaction {
            entityManager.createQuery("DELETE FROM GameGenre link WHERE link.game.id = :id")
                .setParameter("id", fixture.sourceId).executeUpdate()
        }

        // when: 연관 후보를 구할 수 없는 게임을 갱신한다.
        val count = service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.REFRESH)

        // then: 과거 후보를 비우고 정상 빈 결과의 준비 완료 상태를 유지한다.
        assertThat(count).isZero()
        assertThat(storedCandidates(fixture.sourceId)).isEmpty()
        val calculation = calculationRepository.findById(fixture.sourceId).orElseThrow()
        assertThat(calculation.isReady).isTrue()
        assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.SUCCESS)
    }

    @Test
    fun queryFailurePreservesPreviousCandidatesAndRecordsFailureSeparately() {
        // given: 이전 정상 결과가 있고 새 후보 조회가 실패하도록 준비한다.
        val fixture = createFixture(1, 1)
        seedPreviousResult(fixture)
        doThrow(DataAccessResourceFailureException("후보 조회 실패")).`when`(queryRepository)
            .findCandidatesForPrecompute(fixture.sourceId, 20, true)

        // when: 후보 조회에 실패하는 갱신을 실행한다.
        val failure = catchThrowable { service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.REFRESH) }

        // then: 조회 오류를 전달하고 과거 후보·성공 시각과 별도 실패 기록을 유지한다.
        assertThat(failure).isInstanceOf(DataAccessResourceFailureException::class.java).hasMessage("후보 조회 실패")
        assertPreviousResultAndFailedState(fixture)
    }

    @Test
    fun databaseInsertFailureRollsBackDeletionAndSuccessStateButCommitsFailureRecord() {
        // given: 이전 정상 결과와 유효·무효 ID가 섞인 새 후보 목록을 준비한다.
        val fixture = createFixture(1, 1)
        seedPreviousResult(fixture)
        doReturn(listOf(
            RelatedGameCandidateProjection(fixture.candidateIds.first(), BigDecimal("99")),
            RelatedGameCandidateProjection(Long.MAX_VALUE, BigDecimal("98")),
        )).`when`(queryRepository).findCandidatesForPrecompute(fixture.sourceId, 20, true)

        // when: 기존 목록 삭제 후 한 INSERT 안의 유효·무효 후보로 실제 MySQL 외래키 오류를 발생시킨다.
        val failure = catchThrowable { service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.REFRESH) }

        // then: 후보 교체·성공 상태는 롤백하고 실패 기록만 별도로 남긴다.
        assertThat(failure).isInstanceOf(DataIntegrityViolationException::class.java)
            .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException::class.java)
        assertPreviousResultAndFailedState(fixture)
    }

    @Test
    fun stateFailureAfterJdbcInsertRollsBackNewCandidatesAndRestoresOldList() {
        // given: 과거 정상 후보와 성공 상태 전이를 거절할 실패 상태를 준비한다.
        val fixture = createFixture(2, 2)
        seedPreviousResult(fixture)
        inTransaction {
            calculationRepository.findById(fixture.sourceId).orElseThrow()
                .fail(previousSuccess.plusDays(1), "이전 계산 실패")
        }

        // when: JDBC INSERT 이후 성공 상태 변경이 거부되는 후보 교체를 실행한다.
        val failure = catchThrowable {
            store.replace(fixture.sourceId, listOf(
                RelatedGameCandidateProjection(fixture.candidateIds[1], BigDecimal("99.125")),
            ), LocalDateTime.now())
        }

        // then: 후보 교체 전체가 롤백되어 이전 정상 목록과 실패 상태를 보존한다.
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertPreviousResultAndFailedState(fixture)
    }

    @Test
    fun firstCalculationFailureCreatesFailedUnreadyState() {
        // given: 최초 계산의 후보 조회가 실패하도록 준비한다.
        val fixture = createFixture(0, 0)
        doThrow(DataAccessResourceFailureException("최초 계산 실패")).`when`(queryRepository)
            .findCandidatesForPrecompute(fixture.sourceId, 20, false)

        // when: 최초 적재를 실행한다.
        val failure = catchThrowable { service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.INITIAL_LOAD) }

        // then: 조회 오류와 실패 내용을 유지하고 미준비 실패 상태를 저장한다.
        assertThat(failure).isInstanceOf(DataAccessResourceFailureException::class.java)
        val calculation = calculationRepository.findById(fixture.sourceId).orElseThrow()
        assertThat(calculation.isReady).isFalse()
        assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.FAILED)
        assertThat(calculation.lastError).contains("최초 계산 실패")
    }

    @Test
    fun unknownSourceDoesNotCreateCalculationState() {
        // given: 저장되지 않은 기준 게임 ID를 준비한다.
        val missingId = Long.MAX_VALUE

        // when: 존재하지 않는 기준 게임의 최초 계산을 요청한다.
        val failure = catchThrowable { service.precompute(missingId, RelatedGamePrecomputeMode.INITIAL_LOAD) }

        // then: 기존 404 오류를 반환하고 계산 상태를 생성하지 않는다.
        assertThat(failure).isInstanceOfSatisfying(ServiceException::class.java) {
            assertThat(it.resultCode).isEqualTo("404-1")
        }
        assertThat(calculationRepository.existsById(missingId)).isFalse()
    }

    @Test
    fun reloadsStoredCandidateUsingAnEquivalentCompositeKey() {
        // given: 기준 게임과 후보 한 개를 저장한다.
        val fixture = createFixture(1, 0)

        // when: 추천 후보를 계산하여 JDBC로 저장한다.
        service.precompute(fixture.sourceId, RelatedGamePrecomputeMode.INITIAL_LOAD)

        // then: 새 영속성 컨텍스트에서 동일한 복합키로 후보와 필수 점수를 복원한다.
        inTransaction {
            entityManager.clear()
            val key = RelatedGameCandidateId(fixture.sourceId, fixture.candidateIds.first())
            val loaded = entityManager.find(RelatedGameCandidate::class.java, key)
            assertThat(loaded).isNotNull()
            requireNotNull(loaded)
            assertThat(loaded.id).isEqualTo(key)
            assertThat(loaded.relatedScore).isPositive()
            assertThat(loaded.relatedRank).isEqualTo(1)
        }
    }

    private fun assertPreviousResultAndFailedState(fixture: Fixture) {
        assertThat(storedCandidates(fixture.sourceId)).containsExactly(
            RelatedGameCandidateProjection(fixture.candidateIds.first(), BigDecimal("1.0000")),
        )
        val calculation = calculationRepository.findById(fixture.sourceId).orElseThrow()
        assertThat(calculation.isReady).isTrue()
        assertThat(calculation.lastSuccessAt).isEqualTo(previousSuccess)
        assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.FAILED)
        assertThat(calculation.lastError).isNotBlank()
    }

    private fun seedPreviousResult(fixture: Fixture) {
        inTransaction {
            val source = entityManager.getReference(Game::class.java, fixture.sourceId)
            val calculation = RelatedGameCalculation(source)
            calculation.start(previousSuccess.minusMinutes(1))
            calculation.succeed(previousSuccess)
            entityManager.persist(calculation)
            entityManager.persist(RelatedGameCandidate(
                source, entityManager.getReference(Game::class.java, fixture.candidateIds.first()), BigDecimal.ONE, 1,
            ))
        }
    }

    private fun storedCandidates(sourceId: Long): List<RelatedGameCandidateProjection> = inTransaction {
        candidateRepository.findByIdSourceGameIdOrderByRelatedRankAsc(sourceId).map { candidate ->
            RelatedGameCandidateProjection(requireNotNull(requireNotNull(candidate.id).candidateGameId), candidate.relatedScore)
        }
    }

    private fun createFixture(candidateCount: Int, readyCount: Int): Fixture = inTransaction {
        val igdbId = igdbIds.getAndAdd(100)
        val genre = Genre.createFromIgdb(igdbId, "Precompute $igdbId")
        entityManager.persist(genre)
        genreIds.add(requireNotNull(genre.id))
        val source = saveGame(igdbId, BigDecimal("90.00"), genre)
        val candidates = (0 until candidateCount).map { index ->
            val rating = BigDecimal("80.00").add(BigDecimal("0.10").multiply(BigDecimal.valueOf(index.toLong())))
            val candidate = saveGame(igdbId + index + 1, rating, genre)
            if (index < readyCount) {
                val calculation = RelatedGameCalculation(candidate)
                calculation.start(previousSuccess.minusMinutes(1))
                calculation.succeed(previousSuccess)
                entityManager.persist(calculation)
            }
            requireNotNull(candidate.id)
        }
        Fixture(requireNotNull(source.id), java.util.List.copyOf(candidates))
    }

    private fun saveGame(igdbId: Long, rating: BigDecimal, genre: Genre): Game {
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = "Precompute $igdbId", summary = null, cover = null,
            first_release_date = 1704067200L, rating = rating,
            involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        entityManager.persist(game)
        gameIds.add(requireNotNull(game.id))
        entityManager.persist(GameGenre(game, genre))
        return game
    }

    private fun <T : Any> inTransaction(action: () -> T): T =
        requireNotNull(TransactionTemplate(transactionManager).execute { action() })

    private data class Fixture(val sourceId: Long, val candidateIds: List<Long>)

    companion object {
        private val igdbIds = AtomicLong(1_900_000)
    }
}
