package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import java.util.HashMap
import org.springframework.stereotype.Component

// 기준 게임별 연관 추천 순위를 점수로 바꾸고 후보 게임별로 합산
@Component
class PersonalizedRelatedRankScoreCalculator {
    // 각 기준 게임의 연관 추천 순위를 점수로 바꿔 후보 게임 ID별로 합산
    fun calculate(
        relatedGames: MutableList<PersonalizedRelatedGameProjection>
    ): MutableMap<Long, Int> {
        val scoresByGameId: MutableMap<Long, Int> = HashMap()

        for (relatedGame in relatedGames) {
            scoresByGameId.merge(
                relatedGame.gameId,
                scoreForRank(relatedGame.relatedRank),
                { a, b -> a + b },
            )
        }

        return scoresByGameId
    }

    // 연관 추천 1~5위에 약속한 점수를 부여
    private fun scoreForRank(rank: Int): Int {
        return when (rank) {
            1 -> 20
            2 -> 17
            3 -> 14
            4 -> 11
            5 -> 8
            else -> throw IllegalArgumentException("지원하지 않는 연관 추천 순위: " + rank)
        }
    }
}
