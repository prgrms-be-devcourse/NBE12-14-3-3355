package com.gamelog.nbe121423355.domain.notification.dto

import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import java.time.LocalDateTime

data class NotificationResponse(
    val id: Long,
    val kind: NotificationKind,
    val actorName: String?,
    val gameTitle: String?,
    val createdAt: LocalDateTime,
    val read: Boolean,
    val url: String?,
    val unavailable: Boolean,
)
