package com.gamelog.nbe121423355.domain.user.dto

import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGame
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "선호 게임 정보")
data class PreferredGameResponseDto(
    @field:Schema(description = "게임 ID", example = "6") val gameId: Long?,
    @field:Schema(description = "게임명", example = "Baldur's Gate II: Shadows of Amn")
    val gameTitle: String?,
) {
    constructor(preference: UserPreferenceGame) : this(preference.game.id, preference.game.title)
}
