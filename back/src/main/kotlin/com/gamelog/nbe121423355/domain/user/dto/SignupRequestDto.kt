package com.gamelog.nbe121423355.domain.user.dto

import com.gamelog.nbe121423355.global.validation.MaxByteLength
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "회원가입 요청")
@JvmRecord
data class SignupRequestDto(
    @field:NotBlank @field:Schema(description = "닉네임", example = "게임마스터") val nickname: String?,
    @field:NotBlank
    @field:Email
    @field:Schema(description = "이메일", example = "player@example.com")
    val email: String?,
    @field:MaxByteLength(value = 72, message = "비밀번호는 72바이트(영문 기준 72자) 이하여야 합니다.")
    @field:NotBlank
    @field:Size(min = 8)
    @field:Schema(
        description = "비밀번호(8자 이상, 72바이트 이하)",
        example = "password123!",
        minLength = 8,
        maxLength = 72,
    )
    val password: String?,
) {}
