package com.gamelog.nbe121423355.domain.user.dto

import com.gamelog.nbe121423355.domain.user.entity.UserFollow
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

@Schema(description = "팔로우 사용자 페이지")
@JvmRecord
data class FollowPageResponseDto(
    @field:Schema(description = "사용자 목록") val users: MutableList<FollowUserResponseDto>,
    @field:Schema(description = "현재 페이지 번호", example = "0") val page: Int,
    @field:Schema(description = "페이지 크기", example = "10") val size: Int,
    @field:Schema(description = "전체 사용자 수", example = "24") val totalElements: Long,
    @field:Schema(description = "전체 페이지 수", example = "3") val totalPages: Int,
    @field:Schema(description = "다음 페이지 존재 여부", example = "true") val hasNext: Boolean,
) {

    companion object {
        @JvmStatic
        fun from(
            followPage: Page<UserFollow>,
            users: MutableList<FollowUserResponseDto>,
        ): FollowPageResponseDto {
            return FollowPageResponseDto(
                users,
                followPage.getNumber(),
                followPage.size,
                followPage.totalElements,
                followPage.totalPages,
                followPage.hasNext(),
            )
        }
    }
}
