package com.gamelog.nbe121423355.domain.user.dto

import com.gamelog.nbe121423355.domain.user.entity.User
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용자 정보")
@JvmRecord
data class UserDto(
    @field:Schema(description = "사용자 ID", example = "1") val id: Long?,
    @field:Schema(description = "닉네임", example = "게임마스터") val nickname: String,
    @field:Schema(description = "이메일", example = "player@example.com") val email: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true) val profileImageUrl: String?,
    @field:Schema(description = "자기소개", nullable = true) val bio: String?,
    @field:Schema(description = "온보딩 완료 여부", example = "true") val onboardingCompleted: Boolean,
    @field:Schema(description = "사용자 역할", example = "USER", allowableValues = ["USER", "ADMIN"])
    val role: String,
) {
    constructor(
        user: User
    ) : this(
        user.id,
        user.nickname,
        user.email,
        user.profileImageUrl,
        user.bio,
        user.isOnboardingCompleted,
        user.role,
    )
}
