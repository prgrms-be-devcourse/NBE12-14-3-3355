package com.gamelog.nbe121423355.domain.notification

import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewCommentRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notifications: NotificationRepository,
    private val users: UserRepository,
    private val reviews: ReviewRepository,
    private val comments: ReviewCommentRepository,
) {
    data class Item(
        val id: Long,
        val kind: NotificationKind,
        val actorName: String?,
        val gameTitle: String?,
        val createdAt: LocalDateTime,
        val read: Boolean,
        val url: String?,
        val unavailable: Boolean,
    )

    data class Page(val items: List<Item>, val nextCursor: Long?)

    // Synchronous listeners participate in the source transaction; never persist after commit.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    fun receive(event: NotificationRequested) {
        event.recipients.filter { it != event.actorId }.forEach { recipient ->
            if (
                users.findByIdAndDeletedAtIsNull(recipient) != null &&
                !notifications.existsBySourceKeyAndRecipientId(event.sourceKey, recipient)
            ) {
                notifications.save(
                    Notification(
                        recipientId = recipient,
                        actorId = event.actorId,
                        kind = event.kind,
                        targetType = event.targetType,
                        targetId = event.targetId,
                        reviewId = event.reviewId,
                        gameTitle = event.gameTitle,
                        sourceKey = event.sourceKey,
                    )
                )
            }
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    fun cancel(event: NotificationCancelled) {
        notifications.deleteBySourceKey(event.sourceKey)
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    fun cancelComment(event: CommentNotificationsCancelled) {
        notifications.deleteByTargetTypeAndTargetId(NotificationTarget.REVIEW_COMMENT, event.commentId)
    }

    fun unread(userId: Long): Long = notifications.countByRecipientIdAndReadAtIsNull(userId)

    @JvmOverloads
    fun list(userId: Long, before: Long?, unreadOnly: Boolean = false): Page {
        if (before != null && before < 1) {
            throw ServiceException("400-30", "잘못된 알림 커서입니다.")
        }
        val rows = notifications.list(userId, before, unreadOnly, PageRequest.of(0, 21))
        return Page(
            items = rows.take(20).map(::item),
            nextCursor = if (rows.size > 20) rows[19].id else null,
        )
    }

    @Transactional
    fun read(userId: Long, id: Long): Item {
        val notification = notifications.findByIdAndRecipientId(id, userId)
            ?: throw ServiceException("404-30", "알림을 찾을 수 없습니다.")
        notification.read()
        return item(notification)
    }

    @Transactional
    fun readAll(userId: Long) {
        notifications.readAll(userId, LocalDateTime.now())
    }

    private fun item(notification: Notification): Item {
        val actor = notification.actorId?.let { users.findByIdOrNull(it) }
        val actorName = when {
            notification.actorId == null -> null
            actor == null || actor.isDeleted -> "탈퇴한 사용자"
            else -> actor.nickname
        }
        val url = when (notification.targetType) {
            NotificationTarget.USER ->
                users.findByIdAndDeletedAtIsNull(notification.targetId)
                    ?.let { "/profile/${it.id}" }
            NotificationTarget.REVIEW, NotificationTarget.REVIEW_COMMENT -> {
                val review = notification.reviewId?.let { reviews.findActiveById(it).orElse(null) }
                val validComment = notification.targetType != NotificationTarget.REVIEW_COMMENT ||
                    comments.findByIdOrNull(notification.targetId)?.isDeleted == false
                if (review == null || !validComment || review.userGame.user.isDeleted) {
                    null
                } else {
                    val commentParameter = if (notification.targetType == NotificationTarget.REVIEW_COMMENT) {
                        "&notificationComment=${notification.targetId}"
                    } else {
                        ""
                    }
                    "/games/${review.userGame.game.id}?commentReview=${review.id}$commentParameter"
                }
            }
        }
        return Item(
            id = requireNotNull(notification.id),
            kind = notification.kind,
            actorName = actorName,
            gameTitle = notification.gameTitle,
            createdAt = notification.createdAt,
            read = notification.readAt != null,
            url = if (notification.kind == NotificationKind.REVIEW_HIDDEN) null else url,
            unavailable = notification.kind != NotificationKind.REVIEW_HIDDEN && url == null,
        )
    }
}
