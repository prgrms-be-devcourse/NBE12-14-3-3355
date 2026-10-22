package com.gamelog.nbe121423355.domain.game.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "인기 게임 정보")
@JvmRecord
data class PopularGameResponse(
    @field:Schema(description = "GameLog 게임 ID", example = "6") val gameId: Long?,
    @field:Schema(description = "게임명", example = "Baldur's Gate II: Shadows of Amn")
    val title: String?,
    @field:Schema(description = "커버 이미지 URL", nullable = true) val coverImageUrl: String?,
    @field:Schema(description = "좋아하는 게임 등록 수", example = "87") val likeCount: Long,
    @field:Schema(description = "장르 목록") val genres: MutableList<GenreResponse>?,
) {
    @Schema(description = "인기 게임 장르")
    @JvmRecord
    data class GenreResponse(
        @field:Schema(description = "장르 ID", example = "12") val id: Long?,
        @field:Schema(description = "장르명", example = "Role-playing (RPG)") val name: String?,
    ) {}
}
