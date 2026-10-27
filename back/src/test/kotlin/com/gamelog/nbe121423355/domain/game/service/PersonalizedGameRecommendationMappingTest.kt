package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.PersonalizedGameRecordQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedOnboardingPreferenceQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRecommendationCandidateQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRecommendationCandidateProjection
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class PersonalizedGameRecommendationMappingTest {
    private val records = mock(PersonalizedGameRecordQueryRepository::class.java)
    private val preferences = mock(PersonalizedOnboardingPreferenceQueryRepository::class.java)
    private val provider = mock(PersonalizedRelatedGameProvider::class.java)
    private val candidates = mock(PersonalizedRecommendationCandidateQueryRepository::class.java)
    private val rankCalculator = PersonalizedRelatedRankScoreCalculator()
    private val service = PersonalizedGameRecommendationService(
        records, preferences, provider, candidates, PersonalizedGenrePreferenceCalculator(),
        PersonalizedRecordSourceSelector(), rankCalculator, PersonalizedOnboardingScoreCalculator(rankCalculator),
    )

    @Test
    fun `점수가 같으면 평균 평점 출시일 게임 ID 순으로 정렬하고 null은 뒤에 둔다`() {
        // given: 최종 점수는 같고 평균 평점과 출시일 및 ID가 다른 후보 다섯 개를 준비한다.
        prepareOnboarding()
        val related = (1L..5L).map { related(it, it.toInt()) }
        `when`(provider.findTopFiveBySourceGameIds(listOf(100L), listOf(100L))).thenReturn(related)
        val older = LocalDate.of(2020, 1, 1)
        val newer = older.plusYears(1)
        `when`(candidates.findAllByGameIds(listOf(1L, 2L, 3L, 4L, 5L))).thenReturn(listOf(
            candidate(1, "70", 4.0, older), candidate(2, "76", 4.0, newer),
            candidate(3, "82", 3.0, newer), candidate(4, "88", null, newer),
            candidate(5, "94", 4.0, null),
        ))

        // when: 온보딩 선호 게임을 기준으로 맞춤 추천 응답을 생성한다.
        val result = service.recommend(1L)

        // then: 점수 정밀도와 null 정렬 및 장르 없는 게임의 빈 목록을 유지한다.
        assertThat(result.map { it.id }).containsExactly(2L, 1L, 5L, 3L, 4L)
        assertThat(result).allSatisfy {
            assertThat(it.recommendationScore).isEqualByComparingTo("55")
            assertThat(it.genres).isEmpty()
            assertThat(it.coverImageUrl).isNull()
        }
    }

    @Test
    fun `IGDB 평점이 없으면 응답의 null을 유지하고 평점 가산점만 0으로 계산한다`() {
        // given: IGDB 평점과 장르가 없는 후보 하나를 준비한다.
        prepareOnboarding()
        `when`(provider.findTopFiveBySourceGameIds(listOf(100L), listOf(100L)))
            .thenReturn(listOf(related(10, 1)))
        `when`(candidates.findAllByGameIds(listOf(10L)))
            .thenReturn(listOf(candidate(10, null, null, null)))

        // when: 맞춤 추천 점수와 API 응답을 생성한다.
        val result = service.recommend(1L).single()

        // then: 원본 평점은 null로 반환하고 순위 점수 20점만 반영한다.
        assertThat(result.igdbRating).isNull()
        assertThat(result.recommendationScore).isEqualByComparingTo("20")
        assertThat(result.genres).isEmpty()
    }

    @Test
    fun `연관 후보가 최종 게임 조회에서 사라지면 응답에서 제외한다`() {
        // given: 연관 추천에는 있지만 최종 조회에서는 반환되지 않는 게임을 준비한다.
        prepareOnboarding()
        `when`(provider.findTopFiveBySourceGameIds(listOf(100L), listOf(100L)))
            .thenReturn(listOf(related(10, 1)))
        `when`(candidates.findAllByGameIds(listOf(10L))).thenReturn(emptyList())

        // when: 최종 추천 응답을 구성한다.
        val result = service.recommend(1L)

        // then: 사라진 후보를 임의 값으로 채우지 않고 빈 목록을 반환한다.
        assertThat(result).isEmpty()
    }

    private fun prepareOnboarding() {
        `when`(records.findRecordedGameIdsByUserId(1L)).thenReturn(emptyList())
        `when`(records.findEligibleRecordsByUserId(1L)).thenReturn(emptyList())
        `when`(preferences.findPreferredGameIds(1L)).thenReturn(listOf(100L))
        `when`(preferences.findPreferredGenreIds(1L)).thenReturn(emptyList())
    }

    private fun related(id: Long, rank: Int) =
        PersonalizedRelatedGameProjection(100L, id, "게임 $id", null, null, BigDecimal.TEN, rank)

    private fun candidate(id: Long, rating: String?, average: Double?, release: LocalDate?) =
        PersonalizedRecommendationCandidateProjection(
            gameId = id, title = "게임 $id", coverImageUrl = null,
            igdbRating = rating?.let(::BigDecimal), averageRating = average, releaseDate = release,
            genreId = null, genreName = null,
        )
}
