package com.gamelog.nbe121423355.domain.notification

import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewCommentRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import org.springframework.context.event.EventListener
import org.springframework.data.domain.PageRequest
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
    data class Item(val id: Long, val kind: NotificationKind, val actorName: String?, val gameTitle: String?, val createdAt: LocalDateTime, val read: Boolean, val url: String?, val unavailable: Boolean)
    data class Page(val items: List<Item>, val nextCursor: Long?)

    // Synchronous listeners participate in the source transaction; never persist after commit.
    @EventListener @Transactional(propagation = Propagation.MANDATORY)
    fun receive(event: NotificationRequested) {
        event.recipients.filter { it != event.actorId }.forEach { recipient ->
            if (users.findByIdAndDeletedAtIsNull(recipient) != null && !notifications.existsBySourceKeyAndRecipientId(event.sourceKey, recipient)) {
                notifications.save(Notification(recipient, event.actorId, event.kind, event.targetType, event.targetId, event.reviewId, event.gameTitle, event.sourceKey))
            }
        }
    }
    @EventListener @Transactional(propagation = Propagation.MANDATORY)
    fun cancel(event: NotificationCancelled) { notifications.deleteBySourceKey(event.sourceKey) }
    @EventListener @Transactional(propagation = Propagation.MANDATORY)
    fun cancelComment(event: CommentNotificationsCancelled) { notifications.deleteByTargetTypeAndTargetId(NotificationTarget.REVIEW_COMMENT, event.commentId) }

    fun unread(userId: Long): Long = notifications.countByRecipientIdAndReadAtIsNull(userId)
    @JvmOverloads
    fun list(userId: Long, before: Long?, unreadOnly: Boolean = false): Page {
        if (before != null && before < 1) throw ServiceException("400-30", "잘못된 알림 커서입니다.")
        val rows = notifications.list(userId, before, unreadOnly, PageRequest.of(0, 21))
        return Page(rows.take(20).map(::item), if (rows.size > 20) rows[19].id else null)
    }
    @Transactional
    fun read(userId: Long, id: Long): Item {
        val n = notifications.findByIdAndRecipientId(id, userId) ?: throw ServiceException("404-30", "알림을 찾을 수 없습니다.")
        n.read()
        return item(n)
    }
    @Transactional
    fun readAll(userId: Long) { notifications.readAll(userId, LocalDateTime.now()) }

    private fun item(n: Notification): Item {
        val actor = n.actorId?.let { users.findById(it).orElse(null) }
        val actorName = if (n.actorId == null) null else if (actor == null || actor.isDeleted) "탈퇴한 사용자" else actor.nickname
        val url = when (n.targetType) {
            NotificationTarget.USER -> users.findByIdAndDeletedAtIsNull(n.targetId)?.let { "/profile/" + it.id }
            NotificationTarget.REVIEW, NotificationTarget.REVIEW_COMMENT -> {
                val review = n.reviewId?.let { reviews.findActiveById(it).orElse(null) }
                val validComment = n.targetType != NotificationTarget.REVIEW_COMMENT || comments.findById(n.targetId).orElse(null)?.let { !it.isDeleted } == true
                if (review == null || !validComment || review.userGame.user.isDeleted) null
                else "/games/" + review.userGame.game.id + "?commentReview=" + review.id + if (n.targetType == NotificationTarget.REVIEW_COMMENT) "&notificationComment=" + n.targetId else ""
            }
        }
        return Item(requireNotNull(n.id), n.kind, actorName, n.gameTitle, n.createdAt, n.readAt != null, if (n.kind == NotificationKind.REVIEW_HIDDEN) null else url, n.kind != NotificationKind.REVIEW_HIDDEN && url == null)
    }
}
