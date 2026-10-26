package com.gamelog.nbe121423355.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

// 선호 게임 요청Dto
data class PreferredGameRequestDto(
    @field:NotEmpty
    @field:Schema(description = "선호 게임 ID 목록", example = "[6, 9, 12]")
    val gameIds: List<Long>?
)
