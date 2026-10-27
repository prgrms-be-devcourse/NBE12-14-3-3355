package com.gamelog.nbe121423355.domain.user.dto

import com.gamelog.nbe121423355.domain.user.entity.User
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용자 공개 프로필 정보")
data class PublicUserDto(
    @field:Schema(description = "사용자 ID", example = "2") val id: Long?,
    @field:Schema(description = "닉네임", example = "게임친구") val nickname: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true) val profileImageUrl: String?,
    @field:Schema(description = "자기소개", nullable = true) val bio: String?,
) {
    constructor(user: User) : this(user.id, user.nickname, user.profileImageUrl, user.bio)
}
