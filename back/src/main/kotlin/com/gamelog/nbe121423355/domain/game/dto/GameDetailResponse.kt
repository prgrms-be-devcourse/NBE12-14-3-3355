package com.gamelog.nbe121423355.domain.game.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "게임 상세 정보")
@JvmRecord
data class GameDetailResponse(
    @field:Schema(description = "GameLog 게임 ID", example = "6") val id: Long,
    @field:Schema(description = "IGDB 게임 ID", example = "3030") val igdbId: Long?,
    @field:Schema(description = "게임명", example = "Baldur's Gate II: Shadows of Amn")
    val title: String,
    @field:Schema(description = "커버 이미지 URL", nullable = true) val coverImageUrl: String?,
    @field:Schema(description = "개발사", example = "BioWare", nullable = true) val developer: String?,
    @field:Schema(description = "출시일", example = "2000-09-21", nullable = true)
    val releaseDate: LocalDate?,
    @field:Schema(description = "게임 설명", nullable = true) val description: String?,
    @field:Schema(description = "IGDB 평점", example = "89.5", nullable = true)
    val igdbRating: BigDecimal?,
    @field:Schema(description = "장르 목록")
    val genres: List<GenreResponse>,
    @field:Schema(description = "지원 플랫폼 목록") val platforms: List<PlatformResponse>,
    @field:Schema(description = "시리즈 목록") val series: List<SeriesResponse>,
    @field:Schema(description = "GameLog 사용자 기록 통계") val statistics: GameStatisticsResponse,
) {
    @Schema(description = "게임 장르")
    @JvmRecord
    data class GenreResponse(
        @field:Schema(description = "장르 ID", example = "12") val id: Long,
        @field:Schema(description = "장르명", example = "Role-playing (RPG)") val name: String,
    )

    @Schema(description = "게임 플랫폼")
    @JvmRecord
    data class PlatformResponse(
        @field:Schema(description = "플랫폼 ID", example = "6") val id: Long,
        @field:Schema(description = "플랫폼명", example = "PC (Microsoft Windows)") val name: String,
    )

    @Schema(description = "게임 시리즈")
    @JvmRecord
    data class SeriesResponse(
        @field:Schema(description = "시리즈 ID", example = "1") val id: Long,
        @field:Schema(description = "시리즈명", example = "Baldur's Gate") val name: String,
    )
}
