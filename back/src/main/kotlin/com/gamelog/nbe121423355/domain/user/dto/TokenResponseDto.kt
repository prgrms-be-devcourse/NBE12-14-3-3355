package com.gamelog.nbe121423355.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "토큰 재발급 응답")
data class TokenResponseDto(
    @field:Schema(description = "새로 발급한 JWT accessToken", example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String
)
