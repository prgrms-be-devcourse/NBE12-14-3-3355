package com.gamelog.nbe121423355.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema

data class NotificationPageResponse(
    @field:Schema(description = "최신순 알림 목록. 최대 20개")
    val items: List<NotificationResponse>,
    @field:Schema(description = "다음 페이지 조회 시 before에 전달할 알림 ID. 다음 페이지가 없으면 null", example = "104", nullable = true)
    val nextCursor: Long?,
)
