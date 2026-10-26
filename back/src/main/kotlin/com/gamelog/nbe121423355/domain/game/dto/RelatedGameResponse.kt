package com.gamelog.nbe121423355.domain.game.dto

import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.GenreResponse
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "연관 추천 게임")
@JvmRecord
data class RelatedGameResponse @JvmOverloads constructor(
    @field:Schema(description = "GameLog 게임 ID", example = "9") val id: Long,
    @field:Schema(description = "게임명", example = "Baldur's Gate") val title: String,
    @field:Schema(description = "커버 이미지 URL", nullable = true) val coverImageUrl: String?,
    @field:Schema(description = "IGDB 평점", example = "86.2", nullable = true)
    val igdbRating: BigDecimal?,
    @field:Schema(description = "연관 추천 점수", example = "8.5") val recommendationScore: BigDecimal,
    @field:Schema(description = "장르 목록")
    val genres: List<GenreResponse> = emptyList(),
)
