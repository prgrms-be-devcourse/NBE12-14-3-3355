package com.gamelog.nbe121423355.domain.user.dto

import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGenre
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "선호 장르 정보")
@JvmRecord
data class PreferredGenreResponseDto(
    @field:Schema(description = "장르 ID", example = "12") val genreId: Long?,
    @field:Schema(description = "장르명", example = "Role-playing (RPG)") val genreName: String?,
) {
    constructor(preference: UserPreferenceGenre) : this(preference.genre.id, preference.genre.name)
}
