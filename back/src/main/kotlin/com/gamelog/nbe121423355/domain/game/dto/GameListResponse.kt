package com.gamelog.nbe121423355.domain.game.dto

import com.gamelog.nbe121423355.domain.game.entity.Game
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "게임 목록 항목")
@JvmRecord
data class GameListResponse(
    @field:Schema(description = "GameLog 게임 ID", example = "6") val id: Long?,
    @field:Schema(description = "게임명", example = "Baldur's Gate II: Shadows of Amn")
    val title: String?,
    @field:Schema(description = "커버 이미지 URL", nullable = true) val coverImageUrl: String?,
    @field:Schema(description = "출시일", example = "2000-09-21", nullable = true)
    val releaseDate: LocalDate?,
    @field:Schema(description = "IGDB 평점", example = "89.5", nullable = true)
    val igdbRating: BigDecimal?,
) {
    constructor(
        game: Game
    ) : this(game.id, game.title, game.coverImageUrl, game.releaseDate, game.igdbRating)
}
