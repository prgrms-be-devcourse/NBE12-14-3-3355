package com.gamelog.nbe121423355.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

// 선호 장르 오청Dto
@JvmRecord
data class PreferredGenreRequestDto(
    @field:NotEmpty
    @field:Schema(description = "선호 장르 ID 목록", example = "[12, 31, 36]")
    val genreIds: MutableList<Long>?
)
