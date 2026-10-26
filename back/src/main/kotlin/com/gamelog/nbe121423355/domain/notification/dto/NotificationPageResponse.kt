package com.gamelog.nbe121423355.domain.notification.dto

data class NotificationPageResponse(
    val items: List<NotificationResponse>,
    val nextCursor: Long?,
)
