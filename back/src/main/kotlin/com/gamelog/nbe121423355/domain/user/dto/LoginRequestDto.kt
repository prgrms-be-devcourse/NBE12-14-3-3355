package com.gamelog.nbe121423355.domain.user.dto

import com.gamelog.nbe121423355.global.validation.MaxByteLength
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "로그인 요청")
data class LoginRequestDto(
    @field:NotBlank
    @field:Schema(description = "가입 이메일", example = "player@example.com")
    val email: String?,
    @field:MaxByteLength(value = 72, message = "비밀번호는 72바이트(영문 기준 72자) 이하여야 합니다.")
    @field:NotBlank
    @field:Schema(description = "비밀번호", example = "password123!")
    val password: String?,
)
