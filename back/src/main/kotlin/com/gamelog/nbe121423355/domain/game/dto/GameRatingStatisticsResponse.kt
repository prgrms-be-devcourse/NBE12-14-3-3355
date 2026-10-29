package com.gamelog.nbe121423355.domain.game.dto

import java.math.BigDecimal

// 게임의 평균 평점, 평가 수, 내용이 있는 리뷰 수와 평점별 분포를 반환

data class GameRatingStatisticsResponse(
    val averageRating: BigDecimal,
    val ratingCount: Long,
    val reviewCount: Long,
    val ratingDistribution: List<GameRatingDistributionResponse>,
)
