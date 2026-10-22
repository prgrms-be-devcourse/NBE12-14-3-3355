package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import java.util.*
import java.util.HashMap
import java.util.HashSet
import org.springframework.stereotype.Component

// 온보딩 선호 게임의 연관 추천 점수와 선호 장르 가산점을 계산
@Component
class PersonalizedOnboardingScoreCalculator
constructor(private val relatedRankScoreCalculator: PersonalizedRelatedRankScoreCalculator) {
    // 선호 게임별 연관 추천 순위 점수를 합산하고 직접 선택한 게임은 후보에서 제외
    fun calculateRelatedScores(
        preferredGameIds: MutableList<Long>,
        relatedGames: MutableList<PersonalizedRelatedGameProjection>,
    ): MutableMap<Long, Int> {
        val scoresByGameId = HashMap(relatedRankScoreCalculator.calculate(relatedGames))
        preferredGameIds.forEach(scoresByGameId::remove)
        return scoresByGameId
    }

    // 후보 게임과 일치하는 선호 장르마다 10점씩, 최대 30점 부여
    fun calculateGenreBonus(
        preferredGenreIds: MutableList<Long>,
        candidateGenreIds: MutableList<Long>,
    ): Int {
        val preferredGenres = HashSet(preferredGenreIds)
        val matchedGenreCount: Long =
            candidateGenreIds.stream().distinct().filter(preferredGenres::contains).count()
        return Math.min(30, matchedGenreCount * 10).toInt()
    }
}
