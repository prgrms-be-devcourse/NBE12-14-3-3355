package com.gamelog.nbe121423355.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "로그인 응답")
@JvmRecord
data class LoginResponseDto(
    @field:Schema(description = "로그인 사용자 정보") val user: UserDto,
    @field:Schema(description = "API 인증용 JWT accessToken", example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String,
) {}
