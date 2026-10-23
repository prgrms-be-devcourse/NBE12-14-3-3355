package com.gamelog.nbe121423355.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "팔로우 상태")
@JvmRecord
data class FollowStatusResponseDto(
    @field:Schema(description = "대상 사용자 ID", example = "2") val targetUserId: Long?,
    @field:Schema(description = "현재 팔로우 여부", example = "true") val followed: Boolean,
)
