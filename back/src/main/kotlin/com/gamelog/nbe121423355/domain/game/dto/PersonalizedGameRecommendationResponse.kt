package com.gamelog.nbe121423355.domain.game.dto

import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.GenreResponse
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

// 사용자 맞춤 추천 게임의 기본 정보, 추천 점수, 장르를 반환
@Schema(description = "사용자 맞춤 추천 게임")
@JvmRecord
data class PersonalizedGameRecommendationResponse(
    @field:Schema(description = "GameLog 게임 ID", example = "6") val id: Long,
    @field:Schema(description = "게임명", example = "Baldur's Gate II: Shadows of Amn")
    val title: String,
    @field:Schema(description = "커버 이미지 URL", nullable = true) val coverImageUrl: String?,
    @field:Schema(description = "IGDB 평점", example = "89.5", nullable = true)
    val igdbRating: BigDecimal?,
    @field:Schema(description = "개인화 추천 점수", example = "12.7") val recommendationScore: BigDecimal,
    @field:Schema(description = "장르 목록")
    val genres: List<GenreResponse>,
)
