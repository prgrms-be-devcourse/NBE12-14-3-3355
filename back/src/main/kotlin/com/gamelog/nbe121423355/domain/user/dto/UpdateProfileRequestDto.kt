package com.gamelog.nbe121423355.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "프로필 수정 요청")
@JvmRecord
data class UpdateProfileRequestDto(
    @field:NotBlank
    @field:Schema(description = "변경할 닉네임", example = "게임마스터")
    val nickname: String?, // 항상 값이 있어야함
    @field:Schema(
        description = "프로필 이미지 URL",
        example = "https://example.com/profile.png",
        nullable = true,
    )
    val profileImageUrl: String?, // 프로필 사진 없어도됨
    val bio: String?, // 자기소개 없어도됨
) {}
