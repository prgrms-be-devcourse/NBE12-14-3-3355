package com.gamelog.nbe121423355.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "팔로우 목록 사용자 정보")
data class FollowUserResponseDto(
    @field:Schema(description = "사용자 ID", example = "2") val userId: Long?,
    @field:Schema(description = "닉네임", example = "게임친구") val nickname: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true) val profileImageUrl: String?,
    @field:Schema(description = "팔로우 일시") val followedAt: LocalDateTime?,
    @field:Schema(description = "로그인 사용자의 팔로우 여부", example = "true") val followedByMe: Boolean,
    @field:Schema(description = "로그인 사용자 본인 여부", example = "false") val me: Boolean,
)
