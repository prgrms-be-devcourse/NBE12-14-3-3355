package com.gamelog.nbe121423355.domain.notification

import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NotificationCleanupScheduler(private val retention: NotificationRetentionService) {
    // Daily cleanup at 04:15 Korea time; each batch commits independently.
    @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Seoul")
    fun deleteExpiredNotifications() {
        val now = LocalDateTime.now()
        var total = 0
        for (batch in 1..100) {
            val deleted = retention.deleteExpiredBatch(now)
            total += deleted
            if (deleted < 1000) break
        }
        if (total > 0) log.info("보관 기간이 지난 알림 {}건 삭제", total)
    }

    companion object {
        private val log = LoggerFactory.getLogger(NotificationCleanupScheduler::class.java)
    }
}
