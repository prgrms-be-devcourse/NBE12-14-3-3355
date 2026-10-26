package com.gamelog.nbe121423355.domain.notification.service

import com.gamelog.nbe121423355.domain.notification.repository.NotificationRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationRetentionService(private val notifications: NotificationRepository) {
    @Transactional
    fun deleteExpiredBatch(now: LocalDateTime): Int = notifications.deleteExpiredBatch(
        readBefore = now.minusDays(7),
        unreadBefore = now.minusDays(30),
        hiddenBefore = now.minusDays(90),
    )
}
