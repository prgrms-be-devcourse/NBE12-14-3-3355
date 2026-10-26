package com.gamelog.nbe121423355.domain.notification

import java.time.LocalDateTime
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param

interface NotificationRepository : JpaRepository<Notification, Long> {
    @Query("SELECT n FROM Notification n WHERE n.recipientId = :userId AND (:unreadOnly = false OR n.readAt IS NULL) AND (:before IS NULL OR n.id < :before) ORDER BY n.id DESC")
    fun list(
        @Param("userId") userId: Long,
        @Param("before") before: Long?,
        @Param("unreadOnly") unreadOnly: Boolean,
        pageable: Pageable,
    ): List<Notification>

    fun countByRecipientIdAndReadAtIsNull(recipientId: Long): Long
    fun findByIdAndRecipientId(id: Long, recipientId: Long): Notification?
    fun existsBySourceKeyAndRecipientId(sourceKey: String, recipientId: Long): Boolean
    fun deleteBySourceKey(sourceKey: String)
    fun deleteByTargetTypeAndTargetId(targetType: NotificationTarget, targetId: Long)

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientId = :userId AND n.readAt IS NULL")
    fun readAll(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    // Bound each transaction; re-evaluate read state in the DELETE to avoid a read/delete race.
    @Modifying
    @Query(value = """
        DELETE FROM notifications
        WHERE (kind = 'REVIEW_HIDDEN' AND created_at <= :hiddenBefore)
           OR (kind <> 'REVIEW_HIDDEN' AND read_at IS NOT NULL AND read_at <= :readBefore)
           OR (kind <> 'REVIEW_HIDDEN' AND read_at IS NULL AND created_at <= :unreadBefore)
        LIMIT 1000
    """, nativeQuery = true)
    fun deleteExpiredBatch(
        @Param("readBefore") readBefore: LocalDateTime,
        @Param("unreadBefore") unreadBefore: LocalDateTime,
        @Param("hiddenBefore") hiddenBefore: LocalDateTime,
    ): Int
}
