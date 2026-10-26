package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculationStatus
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidate
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties = ["recommendation.related.precomputed-enabled=true"])
@MySqlIntegrationTest
@Transactional
class PrecomputedRelatedGameRecommendationTest {
    @Autowired
    private lateinit var service: GameDetailService

    @Autowired
    private lateinit var em: EntityManager

    private lateinit var source: Game
    private lateinit var genre: Genre
    private val sourceId: Long
        get() = requireNotNull(source.id) { "저장된 연관 추천 기준 게임의 ID가 필요합니다." }

    @BeforeEach
    fun setUp() {
        source = saveGame(9000L, "기준 게임", "90")
        genre = Genre.createFromIgdb(9000L, "RPG")
        em.persist(genre)
        em.persist(GameGenre(source, genre))
    }

    @Test
    fun returnsFiveByStoredRankWithStoredScoresAndCurrentGameInformation() {
        // given: 계산 성공 상태와 저장 순위가 있는 후보 여섯 개를 준비한다.
        calculation(previouslySucceeded = true, status = RelatedGameCalculationStatus.SUCCESS)
        // 현재 평점이 후보 조건에 미달해도 다음 갱신까지 저장된 자격·순위를 유지한다.
        val candidates = (1..6).map { index -> saveGame(9000L + index, "후보 $index", "60") }
        for (index in candidates.indices.reversed()) {
            em.persist(
                RelatedGameCandidate(
                    sourceGame = source,
                    candidateGame = candidates[index],
                    relatedScore = BigDecimal("10.1234").add(BigDecimal.valueOf(index.toLong())),
                    relatedRank = index + 1,
                )
            )
            em.persist(GameGenre(candidates[index], genre))
        }
        em.flush()
        em.clear()

        // when: 사전 계산 조회가 활성화된 상태에서 연관 추천을 조회한다.
        val result = service.getRelatedGames(sourceId)

        // then: 저장 순위 상위 다섯 개와 저장 점수 및 현재 게임 정보를 반환한다.
        assertThat(result.map { it.id }).containsExactlyElementsOf(candidates.take(5).map { it.id })
        assertThat(result.first().recommendationScore).isEqualByComparingTo("10.1234")
        assertThat(result.first().title).isEqualTo("후보 1")
        assertThat(result.first().igdbRating).isEqualByComparingTo("60")
        assertThat(requireNotNull(result.first().genres).map { it.name }).containsExactly("RPG")
    }

    @Test
    fun completedEmptyResultDoesNotFallBackToRealtimeCandidates() {
        // given: 계산에 성공한 빈 저장 결과와 실시간 조회에 적합한 후보를 준비한다.
        calculation(previouslySucceeded = true, status = RelatedGameCalculationStatus.SUCCESS)
        saveEligibleCandidate()
        em.flush()

        // when: 최초 성공 이력이 있는 게임의 연관 추천을 조회한다.
        val result = service.getRelatedGames(sourceId)

        // then: 정상적인 빈 결과를 반환하고 실시간 후보로 대체하지 않는다.
        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(value = RelatedGameCalculationStatus::class, names = ["SUCCESS", "RUNNING", "FAILED"])
    fun previousSuccessRemainsReadableDuringRefreshOrAfterFailure(status: RelatedGameCalculationStatus) {
        // given: 이전 성공 이력과 현재 시도 상태 및 이전 정상 후보를 준비한다.
        calculation(previouslySucceeded = true, status = status)
        val candidate = saveGame(9010L, "이전 정상 후보", "60")
        em.persist(RelatedGameCandidate(source, candidate, BigDecimal("77.5"), 1))
        em.flush()

        // when: 성공·갱신 진행·갱신 실패 상태에서 연관 추천을 조회한다.
        val result = service.getRelatedGames(sourceId)

        // then: 마지막 시도 상태와 관계없이 이전 성공 결과를 반환한다.
        assertThat(result.map { it.id }).containsExactly(candidate.id)
    }

    @Test
    fun missingCalculationUsesExistingQuery() {
        // given: 계산 상태가 없는 기준 게임과 실시간 조회에 적합한 후보를 준비한다.
        val candidate = saveEligibleCandidate()
        em.flush()

        // when: 계산 상태가 없는 게임의 연관 추천을 조회한다.
        val result = service.getRelatedGames(sourceId)

        // then: 기존 실시간 쿼리로 계산한 후보를 반환한다.
        assertThat(result.map { it.id }).containsExactly(candidate.id)
    }

    @ParameterizedTest
    @EnumSource(value = RelatedGameCalculationStatus::class, names = ["PENDING", "RUNNING", "FAILED"])
    fun calculationWithoutSuccessUsesExistingQuery(status: RelatedGameCalculationStatus) {
        // given: 최초 성공 없이 대기·진행·실패 상태인 게임과 실시간 후보를 준비한다.
        calculation(previouslySucceeded = false, status = status)
        val candidate = saveEligibleCandidate()
        em.flush()

        // when: 아직 성공 이력이 없는 게임의 연관 추천을 조회한다.
        val result = service.getRelatedGames(sourceId)

        // then: 계산 상태가 있어도 기존 실시간 쿼리로 후보를 반환한다.
        assertThat(result.map { it.id }).containsExactly(candidate.id)
    }

    @Test
    fun missingGamePreservesExistingError() {
        // given: 저장되지 않은 기준 게임 ID를 준비한다.
        val missingGameId = Long.MAX_VALUE

        // when: 존재하지 않는 게임의 연관 추천을 조회한다.
        val failure = catchThrowable { service.getRelatedGames(missingGameId) }

        // then: 기존 게임 미존재 예외와 메시지를 유지한다.
        assertThat(failure).isInstanceOf(ServiceException::class.java).hasMessage("존재하지 않는 게임입니다.")
    }

    private fun calculation(previouslySucceeded: Boolean, status: RelatedGameCalculationStatus) {
        val calculation = RelatedGameCalculation(source)
        val now = LocalDateTime.now()
        if (previouslySucceeded) {
            calculation.start(now.minusDays(1))
            calculation.succeed(now.minusDays(1))
        }
        when (status) {
            RelatedGameCalculationStatus.RUNNING -> calculation.start(now)
            RelatedGameCalculationStatus.FAILED -> calculation.fail(now, "갱신 실패")
            else -> Unit
        }
        em.persist(calculation)
    }

    private fun saveEligibleCandidate(): Game {
        val candidate = saveGame(9020L, "실시간 후보", "80")
        em.persist(GameGenre(candidate, genre))
        return candidate
    }

    private fun saveGame(igdbId: Long, title: String, rating: String): Game {
        val game = Game.createFromIgdb(
            IgdbGameResponse(
                id = igdbId,
                name = title,
                summary = null,
                cover = null,
                first_release_date = null,
                rating = BigDecimal(rating),
                involved_companies = mutableListOf(),
                genres = mutableListOf(),
                platforms = mutableListOf(),
                collections = mutableListOf(),
            )
        )
        em.persist(game)
        return game
    }
}
