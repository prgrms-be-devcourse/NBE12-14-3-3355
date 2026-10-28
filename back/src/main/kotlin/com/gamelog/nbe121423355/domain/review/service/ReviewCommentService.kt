package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import com.gamelog.nbe121423355.domain.notification.entity.NotificationTarget
import com.gamelog.nbe121423355.domain.review.entity.*
import com.gamelog.nbe121423355.domain.review.repository.*
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import java.time.LocalDateTime
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReviewCommentService(
    private val notificationService: NotificationService,
    private val reviews: ReviewRepository,
    private val comments: ReviewCommentRepository,
    private val likes: ReviewCommentLikeRepository,
    private val users: UserRepository,
) {

    @JvmRecord
    data class Item(
        val id: Long?,
        val parentId: Long?,
        val userId: Long?,
        val nickname: String?,
        val profileImageUrl: String?,
        val content: String?,
        val spoiler: Boolean,
        val deleted: Boolean,
        val edited: Boolean,
        val author: Boolean,
        val mine: Boolean,
        val likeCount: Long,
        val liked: Boolean,
        val replyCount: Long,
        val createdDate: LocalDateTime?,
    )

    @JvmRecord
    data class Page(
        val comments: List<Item>,
        val nextCursor: Long?,
        val totalCount: Long,
    )

    @JvmRecord data class Body(val content: String?, val spoiler: Boolean, val parentId: Long?)

    private fun review(id: Long, lock: Boolean): Review {
        return (if (lock) reviews.findActiveForUpdate(id) else reviews.findActiveById(id))
            .orElseThrow({
                ServiceException(
                    "404-3",
                    "리뷰를 찾을 수 없습니다.",
                )
            })
    }

    private fun comment(reviewId: Long, id: Long, active: Boolean): ReviewComment {
        return comments
            .findById(id)
            .filter({ c -> c.review.id == reviewId && (!active || !c.isDeleted) })
            .orElseThrow({ ServiceException("404-20", "댓글을 찾을 수 없습니다.") })
    }

    private fun owner(c: ReviewComment, userId: Long) {
        if (c.user.id != userId) throw ServiceException("403-20", "본인의 댓글만 변경할 수 있습니다.")
    }

    private fun validate(body: Body) {
        if (body.content == null || body.content.javaIsBlank() || body.content.length > 1000)
            throw ServiceException(
                "400-20",
                "댓글은 공백이 아닌 1~1000자여야 합니다.",
            )
    }

    fun list(
        reviewId: Long,
        viewer: Long?,
        parentId: Long?,
        after: Long?,
    ): Page {
        val review: Review = review(reviewId, false)
        if (after != null && after < 1)
            throw ServiceException(
                "400-20",
                "잘못된 댓글 커서입니다.",
            )
        if (
            parentId != null &&
                comment(
                        reviewId,
                        parentId,
                        false,
                    )
                    .parent != null
        )
            throw ServiceException("400-20", "원댓글의 답글만 조회할 수 있습니다.")
        val size = if (parentId == null) 20 else 10
        val rows: List<ReviewComment> =
            comments.list(reviewId, parentId, after, PageRequest.of(0, size + 1))
        val more = rows.size > size
        val visible = rows.take(size)
        return Page(
            items(review, visible, viewer),
            if (more) visible.last().id else null,
            comments.countByReview_IdAndDeletedFalse(reviewId),
        )
    }

    fun count(reviewId: Long): Long {
        review(reviewId, false)
        return comments.countByReview_IdAndDeletedFalse(reviewId)
    }

    fun get(
        reviewId: Long,
        id: Long,
        viewer: Long?,
    ): Item {
        val review: Review = review(reviewId, false)
        return items(review, listOf(comment(reviewId, id, false)), viewer).first()
    }

    @Transactional
    fun create(
        reviewId: Long,
        userId: Long,
        body: Body,
    ): Item {
        val review: Review = review(reviewId, true)
        validate(body)
        val parent = body.parentId?.let { comment(reviewId, it, active = true) }
        if (parent != null && parent.parent != null)
            throw ServiceException(
                "400-20",
                "답글에는 답글을 달 수 없습니다.",
            )
        val user =
            users.findById(userId).orElseThrow({ ServiceException("404-4", "사용자를 찾을 수 없습니다.") })
        val c: ReviewComment =
            comments.saveAndFlush(ReviewComment(review, user, body.content, body.spoiler, parent))
        val recipients = setOfNotNull(review.userGame.user.id, parent?.user?.id)
        notificationService.create(
            recipients = recipients,
            actorId = userId,
            kind = if (parent == null) NotificationKind.REVIEW_COMMENT else NotificationKind.REVIEW_REPLY,
            targetType = NotificationTarget.REVIEW_COMMENT,
            targetId = requireNotNull(c.id),
            sourceKey = "comment:${c.id}",
            reviewId = reviewId,
            gameTitle = review.userGame.game.title,
        )
        return items(review, listOf(c), userId).first()
    }

    @Transactional
    fun edit(
        reviewId: Long,
        id: Long,
        userId: Long,
        body: Body,
    ): Item {
        val review: Review = review(reviewId, true)
        val c: ReviewComment = comment(reviewId, id, true)
        owner(c, userId)
        validate(body)
        c.edit(body.content, body.spoiler)
        return items(review, listOf(c), userId).first()
    }

    @Transactional
    fun delete(reviewId: Long, id: Long, userId: Long) {
        review(reviewId, true)
        val c = comment(reviewId, id, true)
        owner(c, userId)
        likes.deleteForComment(id)
        c.delete()
        notificationService.cancelComment(id)
    }

    @Transactional
    fun like(
        reviewId: Long,
        id: Long,
        userId: Long,
        value: Boolean,
    ): Item {
        val review: Review = review(reviewId, true)
        val c: ReviewComment = comment(reviewId, id, true)
        val key: ReviewCommentLikeId = ReviewCommentLikeId(userId, id)
        if (value && !likes.existsById(key)) {
            val user =
                users
                    .findById(userId)
                    .orElseThrow({
                        ServiceException(
                            "404-4",
                            "사용자를 찾을 수 없습니다.",
                        )
                    })
            likes.save(ReviewCommentLike(c, user))
            notificationService.create(
                recipients = setOf(requireNotNull(c.user.id)),
                actorId = userId,
                kind = NotificationKind.REVIEW_COMMENT_LIKE,
                targetType = NotificationTarget.REVIEW_COMMENT,
                targetId = id,
                sourceKey = "comment-like:$id:$userId",
                reviewId = reviewId,
                gameTitle = review.userGame.game.title,
            )
        } else if (!value) {
            likes.deleteById(key)
            notificationService.cancel("comment-like:$id:$userId")
        }
        likes.flush()
        return items(review, listOf(c), userId).first()
    }

    @Transactional
    fun purge(reviewId: Long) {
        likes.deleteForReview(reviewId)
        comments.deleteReplies(reviewId)
        comments.deleteForReview(reviewId)
    }

    private fun items(
        review: Review,
        rows: List<ReviewComment>,
        viewer: Long?,
    ): List<Item> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { requireNotNull(it.id) }
        val counts =
            likes
                .counts(ids, viewer)
                .associateBy { it.commentId }
        val replies =
            comments
                .replyCounts(ids)
                .associate { it.parentId to it.count }
        val authorId = review.userGame.user.id
        return rows
            .map { comment ->
                val likeSummary = counts[comment.id]
                val deleted = comment.isDeleted
                Item(
                    id = comment.id,
                    parentId = comment.parent?.id,
                    userId = if (deleted) null else comment.user.id,
                    nickname = if (deleted) null else comment.user.nickname,
                    profileImageUrl = if (deleted) null else comment.user.profileImageUrl,
                    content = if (deleted) "" else comment.content,
                    spoiler = !deleted && comment.isSpoiler,
                    deleted = deleted,
                    edited = !deleted && comment.isEdited,
                    author = !deleted && comment.user.id == authorId,
                    mine = !deleted && comment.user.id == viewer,
                    likeCount = if (deleted) 0 else likeSummary?.count ?: 0,
                    liked = !deleted && likeSummary?.liked == true,
                    replyCount = replies.getOrDefault(comment.id, 0L),
                    createdDate = comment.createdDate,
                )
            }
    }
}
