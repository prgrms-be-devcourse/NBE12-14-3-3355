package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidate
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidateId
import com.gamelog.nbe121423355.support.MySqlTestConfiguration
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Import(MySqlTestConfiguration::class)
@ActiveProfiles("test")
@Transactional
class RelatedGameStorageTest {
    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var candidateRepository: RelatedGameCandidateRepository

    @Autowired
    private lateinit var calculationRepository: RelatedGameCalculationRepository

    @Test
    fun retrievesCandidatesByRankAndPreservesScorePrecision() {
        // given: 저장 순서와 추천 순위가 다른 후보와 소수점 네 자리 점수를 준비한다.
        val source = saveGame(71001L)
        val first = saveGame(71002L)
        val second = saveGame(71003L)
        candidateRepository.saveAll(listOf(
            RelatedGameCandidate(source, second, BigDecimal("80.1250"), 2),
            RelatedGameCandidate(source, first, BigDecimal("90.5550"), 1),
        ))
        entityManager.flush()
        entityManager.clear()

        // when: 새 영속성 컨텍스트에서 기준 게임의 후보를 조회한다.
        val candidates = candidateRepository.findByIdSourceGameIdOrderByRelatedRankAsc(id(source))

        // then: 추천 순위대로 반환하고 소수 점수의 정밀도를 유지한다.
        assertThat(candidates.map { requireNotNull(it.id).candidateGameId }).containsExactly(first.id, second.id)
        assertThat(candidates.first().relatedScore).isEqualByComparingTo("90.5550")
    }

    @Test
    fun rejectsDuplicateCandidatePairInDatabase() {
        // given: 기준·후보 게임 쌍 하나를 이미 저장한 상태를 준비한다.
        val source = saveGame(72001L)
        val target = saveGame(72002L)
        persistCandidate(source, target, 1)
        entityManager.flush()
        entityManager.clear()

        // when: 같은 게임 쌍을 다른 순위로 다시 저장한다.
        val failure = catchThrowable {
            persistCandidate(
                entityManager.getReference(Game::class.java, id(source)),
                entityManager.getReference(Game::class.java, id(target)), 2,
            )
            entityManager.flush()
        }

        // then: 실제 DB에서 중복 게임 쌍을 거절한다.
        assertThat(failure).isInstanceOf(PersistenceException::class.java)
    }

    @Test
    fun rejectsDuplicateRankWithinSourceInDatabase() {
        // given: 같은 기준 게임의 서로 다른 후보에 동일한 순위를 지정한다.
        val source = saveGame(73001L)
        persistCandidate(source, saveGame(73002L), 1)
        persistCandidate(source, saveGame(73003L), 1)

        // when: 후보들을 실제 DB에 반영한다.
        val failure = catchThrowable { entityManager.flush() }

        // then: 기준 게임 내 중복 순위를 DB 제약으로 거절한다.
        assertThat(failure).isInstanceOf(PersistenceException::class.java)
    }

    @Test
    fun deletesOnlyRequestedSourcesCandidates() {
        // given: 서로 다른 기준 게임이 같은 후보를 추천하는 상태를 준비한다.
        val source = saveGame(74001L)
        val otherSource = saveGame(74002L)
        val target = saveGame(74003L)
        persistCandidate(source, target, 1)
        persistCandidate(otherSource, target, 1)
        entityManager.flush()

        // when: 지정한 기준 게임의 후보만 삭제한다.
        val deleted = candidateRepository.deleteAllBySourceGameId(id(source))
        entityManager.clear()

        // then: 한 건만 삭제하고 다른 기준 게임의 후보는 유지한다.
        assertThat(deleted).isEqualTo(1)
        assertThat(candidateRepository.findByIdSourceGameIdOrderByRelatedRankAsc(id(source))).isEmpty()
        assertThat(candidateRepository.findByIdSourceGameIdOrderByRelatedRankAsc(id(otherSource))).hasSize(1)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun nativeGameDeletionCascadesBothCandidateDirectionsAndCalculation(deleteSource: Boolean) {
        // given: 기준·후보 게임과 양쪽 계산 상태를 저장한다.
        val source = saveGame(75001L)
        val target = saveGame(75002L)
        persistCandidate(source, target, 1)
        calculationRepository.save(RelatedGameCalculation(source))
        calculationRepository.save(RelatedGameCalculation(target))
        entityManager.flush()
        entityManager.clear()
        val deletedId = if (deleteSource) id(source) else id(target)
        val retainedId = if (deleteSource) id(target) else id(source)

        // when: IGDB 동기화처럼 JPA 삭제 훅을 거치지 않는 경로로 게임을 삭제한다.
        entityManager.createNativeQuery("DELETE FROM games WHERE id = :gameId")
            .setParameter("gameId", deletedId).executeUpdate()
        entityManager.clear()

        // then: 어느 방향을 삭제해도 후보 관계와 삭제 게임의 상태만 함께 제거한다.
        assertThat(candidateRepository.existsById(RelatedGameCandidateId(id(source), id(target)))).isFalse()
        assertThat(calculationRepository.existsById(deletedId)).isFalse()
        assertThat(calculationRepository.existsById(retainedId)).isTrue()
    }

    @Test
    fun failedRefreshStaysReadyAndInitialFailuresRemainInPagedUnreadyQuery() {
        // given: 성공 후 갱신 실패·최초 실패·대기 상태인 세 게임을 준비한다.
        val ready = saveGame(76001L)
        val failed = saveGame(76002L)
        val pending = saveGame(76003L)
        val attemptedAt = LocalDateTime.of(2026, 10, 21, 10, 0)
        val readyCalculation = RelatedGameCalculation(ready)
        readyCalculation.start(attemptedAt)
        readyCalculation.succeed(attemptedAt.plusMinutes(1))
        readyCalculation.fail(attemptedAt.plusDays(1), "갱신 실패")
        val failedCalculation = RelatedGameCalculation(failed)
        failedCalculation.fail(attemptedAt, "최초 계산 실패")
        calculationRepository.saveAll(listOf(readyCalculation, failedCalculation, RelatedGameCalculation(pending)))
        entityManager.flush()
        entityManager.clear()

        // when: 준비 완료 여부와 한 건씩의 미준비 페이지를 조회한다.
        val readyExists = calculationRepository.existsByGameIdAndLastSuccessAtIsNotNull(id(ready))
        val failedExists = calculationRepository.existsByGameIdAndLastSuccessAtIsNotNull(id(failed))
        val firstPage = calculationRepository.findUnreadyGameIdsAfter(0L, PageRequest.of(0, 1))
        val nextPage = calculationRepository.findUnreadyGameIdsAfter(id(failed), PageRequest.of(0, 1))

        // then: 이전 성공은 준비 완료이며 최초 실패와 대기 게임만 ID순으로 반환한다.
        assertThat(readyExists).isTrue()
        assertThat(failedExists).isFalse()
        assertThat(firstPage).containsExactly(failed.id)
        assertThat(nextPage).containsExactly(pending.id)
    }

    private fun persistCandidate(source: Game, target: Game, rank: Int) {
        entityManager.persist(RelatedGameCandidate(source, target, BigDecimal("90.1250"), rank))
    }

    private fun saveGame(igdbId: Long): Game {
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = "Game $igdbId", summary = null, cover = null,
            first_release_date = 1704067200L, rating = BigDecimal("80.25"),
            involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        entityManager.persist(game)
        return game
    }

    private fun id(game: Game): Long = requireNotNull(game.id) { "저장된 게임의 ID가 필요합니다." }
}
