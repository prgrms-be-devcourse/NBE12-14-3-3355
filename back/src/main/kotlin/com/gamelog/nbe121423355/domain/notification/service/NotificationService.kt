package com.gamelog.nbe121423355.domain.notification.service

import com.gamelog.nbe121423355.domain.board.repository.BoardPostRepository
import com.gamelog.nbe121423355.domain.notification.dto.NotificationPageResponse
import com.gamelog.nbe121423355.domain.notification.dto.NotificationResponse
import com.gamelog.nbe121423355.domain.notification.repository.NotificationRepository
import com.gamelog.nbe121423355.domain.notification.entity.Notification
import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import com.gamelog.nbe121423355.domain.notification.entity.NotificationTarget
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewCommentRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
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
    private val boardPosts: BoardPostRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val writeTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = Propagation.REQUIRES_NEW.value()
    }

    // 원본이 커밋된 경우에만 실행하며, 알림 실패는 원본 요청에 전파하지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    fun create(
        recipients: Set<Long>,
        actorId: Long?,
        kind: NotificationKind,
        targetType: NotificationTarget,
        targetId: Long,
        sourceKey: String,
        reviewId: Long? = null,
        gameTitle: String? = null,
    ) {
        val recipientsToNotify = recipients.filter { it != actorId }
        afterCommit("create:$sourceKey") {
            recipientsToNotify.forEach { recipient ->
                if (
                    users.findByIdAndDeletedAtIsNull(recipient) != null &&
                    !notifications.existsBySourceKeyAndRecipientId(sourceKey, recipient)
                ) {
                    notifications.save(
                        Notification(
                            recipientId = recipient,
                            actorId = actorId,
                            kind = kind,
                            targetType = targetType,
                            targetId = targetId,
                            reviewId = reviewId,
                            gameTitle = gameTitle,
                            sourceKey = sourceKey,
                        )
                    )
                }
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun cancel(sourceKey: String) {
        afterCommit("cancel:$sourceKey") {
            notifications.deleteBySourceKey(sourceKey)
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun cancelComment(commentId: Long) {
        afterCommit("cancel-comment:$commentId") {
            notifications.deleteByTargetTypeAndTargetId(NotificationTarget.REVIEW_COMMENT, commentId)
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun cancelBoardPost(postId: Long) {
        afterCommit("cancel-board-post:$postId") {
            notifications.deleteByTargetTypeAndTargetId(NotificationTarget.BOARD_POST, postId)
        }
    }

    private fun afterCommit(operation: String, action: () -> Unit) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                try {
                    writeTransaction.executeWithoutResult { action() }
                } catch (exception: Exception) {
                    log.error("커밋 후 알림 처리 실패 - operation={}", operation, exception)
                }
            }
        })
    }

    fun unread(userId: Long): Long = notifications.countByRecipientIdAndReadAtIsNull(userId)

    fun list(userId: Long, before: Long?, unreadOnly: Boolean = false): NotificationPageResponse {
        if (before != null && before < 1) {
            throw ServiceException("400-30", "잘못된 알림 커서입니다.")
        }
        val rows = notifications.list(userId, before, unreadOnly, PageRequest.of(0, 21))
        return NotificationPageResponse(
            items = rows.take(20).map(::item),
            nextCursor = if (rows.size > 20) rows[19].id else null,
        )
    }

    @Transactional
    fun read(userId: Long, id: Long): NotificationResponse {
        val notification = notifications.findByIdAndRecipientId(id, userId)
            ?: throw ServiceException("404-30", "알림을 찾을 수 없습니다.")
        notification.read()
        return item(notification)
    }

    @Transactional
    fun readAll(userId: Long) {
        notifications.readAll(userId, LocalDateTime.now())
    }

    private fun item(notification: Notification): NotificationResponse {
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
                val review = notification.reviewId?.let { reviews.findActiveById(it) }
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
            NotificationTarget.BOARD_POST ->
                boardPosts.findVisibleById(notification.targetId)
                    ?.let { "/community?board=free&post=${it.id}" }
        }
        return NotificationResponse(
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

    companion object {
        private val log = LoggerFactory.getLogger(NotificationService::class.java)
    }
}
