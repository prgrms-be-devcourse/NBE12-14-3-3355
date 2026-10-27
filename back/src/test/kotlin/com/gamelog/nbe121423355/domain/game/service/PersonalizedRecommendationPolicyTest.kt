package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedGameRecordProjection
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test

class PersonalizedRecommendationPolicyTest {
    @Test
    fun `미평가 또는 별점 3점5 이상인 기록에서 중복 없이 추천 출발 게임을 선택한다`() {
        // given: 미평가와 경계 별점 및 중복 장르 행이 있는 기록을 준비한다.
        val records = listOf(
            record(1, 10, null), record(2, 10, "3.50"), record(2, 20, "3.50"),
            record(3, 10, "3.49"),
        )

        // when: 추천 출발 게임을 선택한다.
        val result = PersonalizedRecordSourceSelector().selectSourceGameIds(records)

        // then: 미평가와 경계 별점 게임만 최초 순서대로 한 번씩 반환한다.
        assertThat(result).containsExactly(1L, 2L)
    }

    @Test
    fun `게임별 장르 기여도로 별점을 가중 평균하고 장르 없는 기록은 분모에서 제외한다`() {
        // given: 두 장르의 5점 게임과 한 장르의 3점 게임 및 장르 없는 게임을 준비한다.
        val records = listOf(
            record(1, 10, "5.0"), record(1, 20, "5.0"), record(1, 10, "5.0"),
            record(2, 10, "3.0"), record(3, null, "1.0"),
        )

        // when: 기록에서 장르 선호도를 계산한다.
        val result = PersonalizedGenrePreferenceCalculator().calculate(records)

        // then: 중복 장르를 한 번만 반영하고 기존 기여도 및 가중 평균 계산식을 유지한다.
        assertThat(result.map { it.genreId }).containsExactly(10L, 20L)
        assertThat(result[0].playRatio).isEqualTo(0.75)
        assertThat(result[0].averageRating).isCloseTo(11.0 / 3.0, offset(1e-12))
        assertThat(result[0].score).isCloseTo(74.0, offset(1e-12))
        assertThat(result[1].playRatio).isEqualTo(0.25)
        assertThat(result[1].averageRating).isEqualTo(5.0)
        assertThat(result[1].score).isEqualTo(70.0)
    }

    @Test
    fun `미평가 장르의 평균은 null이며 선호 점수가 같으면 장르 ID 순으로 정렬한다`() {
        // given: 별점 없이 두 장르를 가진 게임을 장르 ID 역순으로 준비한다.
        val records = listOf(record(1, 20, null), record(1, 10, null))

        // when: 장르 선호도를 계산한다.
        val result = PersonalizedGenrePreferenceCalculator().calculate(records)

        // then: 별점을 임의 값으로 채우지 않고 플레이 비율만 점수에 반영한다.
        assertThat(result.map { it.genreId }).containsExactly(10L, 20L)
        assertThat(result).allSatisfy {
            assertThat(it.averageRating).isNull()
            assertThat(it.score).isEqualTo(20.0)
        }
    }

    @Test
    fun `장르가 없는 기록만 있으면 장르 선호도는 비어 있다`() {
        // given: 장르가 없는 미평가 기록을 준비한다.
        val records = listOf(record(1, null, null))

        // when: 장르 선호도를 계산한다.
        val result = PersonalizedGenrePreferenceCalculator().calculate(records)

        // then: 나눗셈 없이 빈 장르 선호도를 반환한다.
        assertThat(result).isEmpty()
    }

    @Test
    fun `연관 순위 점수를 합산하고 온보딩에서 직접 선택한 게임은 제외한다`() {
        // given: 같은 후보의 서로 다른 순위와 직접 선택한 게임을 포함한 연관 추천을 준비한다.
        val related = listOf(row(1, 10, 1), row(2, 10, 2), row(1, 20, 3), row(2, 30, 4), row(2, 40, 5))
        val rankCalculator = PersonalizedRelatedRankScoreCalculator()

        // when: 순위 점수와 온보딩 후보 점수를 각각 계산한다.
        val rankScores = rankCalculator.calculate(related)
        val onboardingScores = PersonalizedOnboardingScoreCalculator(rankCalculator)
            .calculateRelatedScores(listOf(20L), related)

        // then: 순위별 약속한 점수를 합산하고 직접 선택한 후보만 제외한다.
        assertThat(rankScores).containsExactlyInAnyOrderEntriesOf(mapOf(10L to 37, 20L to 14, 30L to 11, 40L to 8))
        assertThat(onboardingScores).containsExactlyInAnyOrderEntriesOf(mapOf(10L to 37, 30L to 11, 40L to 8))
    }

    @Test
    fun `연관 순위 1위부터 5위 이외의 점수 계산은 거부한다`() {
        // given: 지원하지 않는 6위 연관 추천을 준비한다.
        val calculator = PersonalizedRelatedRankScoreCalculator()
        val related = listOf(row(1, 10, 6))

        // when: 잘못된 순위의 점수를 계산한다.
        val failure = catchThrowable { calculator.calculate(related) }

        // then: 임의 점수를 부여하지 않고 잘못된 입력임을 알린다.
        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `온보딩 장르 가산점은 중복 없이 계산하고 최대 30점으로 제한한다`() {
        // given: 중복이 있는 네 개의 선호 장르를 준비한다.
        val calculator = PersonalizedOnboardingScoreCalculator(PersonalizedRelatedRankScoreCalculator())
        val preferred = listOf(10L, 20L, 20L, 30L, 40L)

        // when: 중복 장르와 최대 일치 수를 넘는 후보 및 불일치 후보를 계산한다.
        val duplicate = calculator.calculateGenreBonus(preferred, listOf(10L, 10L))
        val capped = calculator.calculateGenreBonus(preferred, listOf(10L, 20L, 30L, 40L))
        val unmatched = calculator.calculateGenreBonus(preferred, listOf(50L))

        // then: 고유 장르당 10점을 부여하되 30점을 넘기지 않는다.
        assertThat(duplicate).isEqualTo(10)
        assertThat(capped).isEqualTo(30)
        assertThat(unmatched).isZero()
    }

    private fun record(gameId: Long, genreId: Long?, rating: String?) =
        PersonalizedGameRecordProjection(gameId, genreId, rating?.let(::BigDecimal))

    private fun row(sourceId: Long, gameId: Long, rank: Int) =
        PersonalizedRelatedGameProjection(sourceId, gameId, "후보", null, null, BigDecimal.TEN, rank)
}
