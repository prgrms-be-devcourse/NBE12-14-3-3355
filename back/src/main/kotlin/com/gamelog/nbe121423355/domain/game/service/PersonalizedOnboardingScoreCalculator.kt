package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import org.springframework.stereotype.Component

// 온보딩 선호 게임의 연관 추천 점수와 선호 장르 가산점을 계산
@Component
class PersonalizedOnboardingScoreCalculator(private val relatedRankScoreCalculator: PersonalizedRelatedRankScoreCalculator) {
    // 선호 게임별 연관 추천 순위 점수를 합산하고 직접 선택한 게임은 후보에서 제외
    fun calculateRelatedScores(
        preferredGameIds: List<Long>,
        relatedGames: List<PersonalizedRelatedGameProjection>,
    ): Map<Long, Int> {
        val scoresByGameId = relatedRankScoreCalculator.calculate(relatedGames).toMutableMap()
        preferredGameIds.forEach(scoresByGameId::remove)
        return scoresByGameId
    }

    // 후보 게임과 일치하는 선호 장르마다 10점씩, 최대 30점 부여
    fun calculateGenreBonus(
        preferredGenreIds: List<Long>,
        candidateGenreIds: List<Long>,
    ): Int {
        val preferredGenres = preferredGenreIds.toSet()
        val matchedGenreCount = candidateGenreIds.distinct().count { it in preferredGenres }
        return (matchedGenreCount.toLong() * 10).coerceAtMost(30).toInt()
    }
}
