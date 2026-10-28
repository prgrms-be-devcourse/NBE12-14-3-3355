package com.gamelog.nbe121423355.domain.notification.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

// IDs intentionally have no foreign keys: notices survive target deletion and withdrawal.
@Entity
@Table(
    name = "notifications",
    indexes = [
        Index(name = "idx_notification_recipient_id", columnList = "recipient_id,id"),
        Index(name = "idx_notification_unread", columnList = "recipient_id,read_at"),
        Index(name = "idx_notification_created", columnList = "created_at"),
        Index(name = "idx_notification_read", columnList = "read_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_notification_source_recipient", columnNames = ["source_key", "recipient_id"]),
    ],
)
class Notification(
    @Column(nullable = false)
    val recipientId: Long,
    val actorId: Long?,

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val kind: NotificationKind,

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val targetType: NotificationTarget,

    @Column(nullable = false)
    val targetId: Long,
    val reviewId: Long?,

    @Column(length = 255)
    val gameTitle: String?,

    @Column(nullable = false, length = 160)
    val sourceKey: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()

    var readAt: LocalDateTime? = null
        protected set

    fun read() {
        if (readAt == null) {
            readAt = LocalDateTime.now()
        }
    }
}
