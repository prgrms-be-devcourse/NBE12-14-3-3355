package com.gamelog.nbe121423355.domain.game.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "게임 커뮤니티 기록 통계")
data class GameStatisticsResponse(
    @field:Schema(description = "플레이 완료 사용자 수", example = "120") val playedCount: Long,
    @field:Schema(description = "플레이 중 사용자 수", example = "15") val playingCount: Long,
    @field:Schema(description = "플레이 예정 사용자 수", example = "32") val backlogCount: Long,
    @field:Schema(description = "위시리스트 사용자 수", example = "41") val wishlistCount: Long,
    @field:Schema(description = "좋아하는 게임 등록 수", example = "87") val likeCount: Long,
    @field:Schema(description = "사용자 평균 별점", example = "4.3") val averageRating: BigDecimal,
    @field:Schema(description = "별점 등록 수", example = "56") val ratingCount: Long,
    @field:Schema(description = "내용이 작성된 리뷰 수", example = "31") val reviewCount: Long,
    @field:Schema(description = "별점별 분포")
    val ratingDistribution: List<GameRatingDistributionResponse>,
    @field:Schema(description = "평균 플레이 시간", example = "42.5")
    val averagePlayTimeHours: BigDecimal,
    @field:Schema(description = "플레이 시간 기록 사용자 수", example = "20") val playTimeUserCount: Long,
)
