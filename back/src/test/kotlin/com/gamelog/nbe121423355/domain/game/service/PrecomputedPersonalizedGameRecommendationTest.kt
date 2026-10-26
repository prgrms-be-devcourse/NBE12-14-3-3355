package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidate
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGame
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGenre
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

// 기존 기록·온보딩·장르 전용 시나리오도 전환을 켠 상태에서 함께 검증한다.
@SpringBootTest(properties = ["recommendation.personalized.precomputed-enabled=true"])
@MySqlIntegrationTest
class PrecomputedPersonalizedGameRecommendationTest : PersonalizedGameRecommendationServiceTest() {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var service: PersonalizedGameRecommendationService
    @Autowired private lateinit var provider: PersonalizedRelatedGameProvider
    @Autowired private lateinit var repository: PersonalizedRelatedGameQueryRepository

    @Test
    fun recordExclusionReranksStoredCandidatesWithoutAddingStoredScore() {
        // given: 저장 후보 중 한 게임을 중단 기록으로 제외하고 이전 성공 후 실패 상태를 준비한다.
        val user = user("stored-record")
        val genre = genre()
        val source = game(8100L, genre)
        record(user, source, PlayStatus.COMPLETED)
        val candidates = candidates(source, genre)
        record(user, candidates[1], PlayStatus.DROPPED)
        ready(source, failedRefresh = true)
        em.flush()

        // when: 저장 후보를 사용하는 맞춤 추천을 조회한다.
        val result = service.recommend(requireNotNull(user.id))

        // then: 제외 후 순위를 다시 매기고 저장 점수를 더하지 않은 개인화 점수를 반환한다.
        assertThat(result.map { it.id }).containsExactly(
            candidates[0].id, candidates[2].id, candidates[3].id, candidates[4].id, candidates[5].id,
        )
        assertThat(result[0].recommendationScore).isEqualByComparingTo("70")
        assertThat(result[1].recommendationScore).isEqualByComparingTo("67")
    }

    @Test
    fun onboardingExcludesPreferredGamesAndSumsDuplicateRanks() {
        // given: 두 선호 게임의 저장 후보에 서로와 공통 후보 다섯 개가 포함된 상태를 준비한다.
        val user = user("stored-onboarding")
        val genre = genre()
        val first = game(8200L, genre)
        val second = game(8201L, genre)
        em.persist(UserPreferenceGame(user, first))
        em.persist(UserPreferenceGame(user, second))
        em.persist(UserPreferenceGenre(user, genre))
        val candidates = (1..5).map { game(8210L + it, genre) }
        for (source in listOf(first, second)) {
            ready(source, failedRefresh = false)
            em.persist(RelatedGameCandidate(source, if (source === first) second else first, BigDecimal("999"), 1))
            candidates.forEachIndexed { index, candidate ->
                em.persist(RelatedGameCandidate(source, candidate, BigDecimal("999"), index + 2))
            }
        }
        em.flush()

        // when: 온보딩 선호 게임을 기준으로 맞춤 추천을 조회한다.
        val result = service.recommend(requireNotNull(user.id))

        // then: 직접 선택한 게임을 제외하고 공통 후보의 재순위 점수를 합산한다.
        assertThat(result.map { it.id }).containsExactlyElementsOf(candidates.map { it.id })
        assertThat(result.first().recommendationScore).isEqualByComparingTo("90")
    }

    @Test
    fun insufficientStoredCandidatesAreReplacedByExistingExcludedQuery() {
        // given: 저장 후보 여섯 개 중 세 개를 제외하고 저장 범위 밖 후보를 추가한다.
        val genre = genre()
        val source = game(8300L, genre)
        val candidates = candidates(source, genre)
        ready(source, failedRefresh = false)
        val excluded = candidates.take(3).map { requireNotNull(it.id) }
        // 저장 범위 밖의 후보도 기존 SQL의 보충 결과에 포함된다.
        game(8399L, genre)
        em.flush()
        val sourceIds = listOf(requireNotNull(source.id))

        // when: 저장 후보가 부족한 기준 게임의 개인별 후보를 조회한다.
        val result = provider.findTopFiveBySourceGameIds(sourceIds, excluded)

        // then: 제외 조건을 적용한 기존 실시간 쿼리의 결과로 목록을 대체한다.
        assertThat(result).isEqualTo(repository.findTopFiveBySourceGameIds(sourceIds, excluded))
    }

    @Test
    fun neverUsesStoredRowsWithoutSuccessfulCalculation() {
        // given: 후보 행은 저장됐지만 최초 계산이 실패한 기준 게임을 준비한다.
        val genre = genre()
        val source = game(8400L, genre)
        candidates(source, genre)
        val calculation = RelatedGameCalculation(source)
        calculation.fail(LocalDateTime.now(), "최초 실패")
        em.persist(calculation)
        em.flush()
        val sourceIds = listOf(requireNotNull(source.id))

        // when: 저장 후보 조회와 후보 공급 경로를 각각 호출한다.
        val stored = repository.findStoredTopFiveBySourceGameIds(sourceIds, emptyList())
        val result = provider.findTopFiveBySourceGameIds(sourceIds, emptyList())

        // then: 성공 이력 없는 저장 행은 배제하고 기존 실시간 쿼리와 같은 결과를 반환한다.
        assertThat(stored).isEmpty()
        assertThat(result).isEqualTo(repository.findTopFiveBySourceGameIds(sourceIds, emptyList()))
    }

    private fun candidates(source: Game, genre: Genre): List<Game> = (1..6).map { rank ->
        val candidate = game(requireNotNull(source.igdbId) + rank + 10, genre)
        em.persist(RelatedGameCandidate(source, candidate, BigDecimal("999"), rank))
        candidate
    }

    private fun ready(source: Game, failedRefresh: Boolean) {
        val state = RelatedGameCalculation(source)
        state.start(LocalDateTime.now())
        state.succeed(LocalDateTime.now())
        if (failedRefresh) state.fail(LocalDateTime.now(), "재갱신 실패")
        em.persist(state)
    }

    private fun genre(): Genre {
        val genre = Genre.createFromIgdb(8000L, "RPG")
        em.persist(genre)
        return genre
    }

    private fun game(igdbId: Long, genre: Genre): Game {
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = "게임 $igdbId", summary = null, cover = null, first_release_date = null,
            rating = BigDecimal("80"), involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        em.persist(game)
        em.persist(GameGenre(game, genre))
        return game
    }

    private fun user(name: String): User {
        val user = User(name, "$name@test.com", "password")
        em.persist(user)
        return user
    }

    private fun record(user: User, game: Game, status: PlayStatus) {
        val record = UserGame(user, game)
        record.changePlayStatus(status)
        em.persist(record)
    }
}
