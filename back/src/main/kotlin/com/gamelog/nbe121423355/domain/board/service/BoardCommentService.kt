package com.gamelog.nbe121423355.domain.board.service

import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.board.dto.BoardAuthorResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardCommentPageResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardCommentRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardCommentResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardCommentUpdateRequest
import com.gamelog.nbe121423355.domain.board.entity.BoardComment
import com.gamelog.nbe121423355.domain.board.repository.BoardCommentRepository
import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import com.gamelog.nbe121423355.domain.notification.entity.NotificationTarget
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.ZoneId
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BoardCommentService(
    private val notificationService: NotificationService,
    private val postService: BoardPostService,
    private val comments: BoardCommentRepository,
) {
    fun list(viewerId: Long, postId: Long, parentId: Long?, after: Long?): BoardCommentPageResponse {
        postService.visible(postId)
        if (after != null && after < 1) throw ServiceException("400-40", "잘못된 댓글 커서입니다.")
        if (parentId != null && comment(postId, parentId).parent != null) {
            throw ServiceException("400-41", "원댓글의 답글만 조회할 수 있습니다.")
        }
        val size = if (parentId == null) ROOT_PAGE_SIZE else REPLY_PAGE_SIZE
        val rows = comments.list(postId, parentId, after, PageRequest.of(0, size + 1))
        val visible = rows.take(size)
        return BoardCommentPageResponse(
            comments = responses(visible, viewerId),
            nextCursor = if (rows.size > size) visible.last().id else null,
            totalCount = comments.countByPost_IdAndDeletedFalseAndHiddenFalse(postId),
        )
    }

    @Transactional
    fun create(viewerId: Long, postId: Long, request: BoardCommentRequest): BoardCommentResponse {
        val post = postService.lockVisible(postId)
        val parent = request.parentId?.let { comment(postId, it) }
        if (parent != null && parent.parent != null) {
            throw ServiceException("400-41", "답글에는 답글을 달 수 없습니다.")
        }
        if (parent != null && parent.isRemoved) {
            throw ServiceException("400-41", "삭제된 댓글에는 답글을 달 수 없습니다.")
        }
        val saved =
            comments.saveAndFlush(
                BoardComment(
                    post = post,
                    user = postService.activeUser(viewerId),
                    parent = parent,
                    content = requireNotNull(request.content).trim(),
                    spoiler = request.spoiler,
                )
            )
        notificationService.create(
            recipients = setOfNotNull(post.user.id, parent?.user?.id),
            actorId = viewerId,
            kind = if (parent == null) NotificationKind.BOARD_COMMENT else NotificationKind.BOARD_REPLY,
            targetType = NotificationTarget.BOARD_POST,
            targetId = postId,
            sourceKey = sourceKey(checkNotNull(saved.id)),
            gameTitle = post.title,
        )
        return responses(listOf(saved), viewerId).first()
    }

    @Transactional
    fun update(viewerId: Long, postId: Long, commentId: Long, request: BoardCommentUpdateRequest): BoardCommentResponse {
        postService.lockVisible(postId)
        val comment = activeComment(postId, commentId)
        postService.requireOwner(comment.user, viewerId)
        comment.edit(requireNotNull(request.content).trim(), request.spoiler)
        return responses(listOf(comment), viewerId).first()
    }

    @Transactional
    fun delete(viewerId: Long, postId: Long, commentId: Long) {
        postService.lockVisible(postId)
        val comment = activeComment(postId, commentId)
        postService.requireOwner(comment.user, viewerId)
        comment.delete()
        notificationService.cancel(sourceKey(commentId))
    }

    fun comment(postId: Long, commentId: Long): BoardComment =
        comments.findByIdOrNull(commentId)?.takeIf { it.post.id == postId }
            ?: throw ServiceException("404-41", "댓글을 찾을 수 없습니다.")

    fun activeComment(postId: Long, commentId: Long): BoardComment =
        comment(postId, commentId).takeIf { !it.isRemoved }
            ?: throw ServiceException("404-41", "댓글을 찾을 수 없습니다.")

    private fun responses(rows: List<BoardComment>, viewerId: Long): List<BoardCommentResponse> {
        if (rows.isEmpty()) return emptyList()
        val rootIds = rows.filter { it.parent == null }.map { checkNotNull(it.id) }
        val replyCounts =
            if (rootIds.isEmpty()) emptyMap() else comments.replyCounts(rootIds).associate { it.id to it.count }
        return rows.map { comment ->
            val removed = comment.isRemoved
            BoardCommentResponse(
                id = checkNotNull(comment.id),
                parentId = comment.parent?.id,
                author = if (removed) null else BoardAuthorResponse.from(comment.user),
                content = if (removed) "" else comment.content,
                spoiler = !removed && comment.spoiler,
                removed = removed,
                edited = !removed && comment.edited,
                mine = !removed && comment.user.id == viewerId,
                replyCount = replyCounts[comment.id] ?: 0,
                // JPA auditing이 사용하는 서버 기본 시간대를 붙여 브라우저의 로컬 시간 해석을 방지한다.
                createdDate = comment.createdDate?.atZone(ZoneId.systemDefault())?.toOffsetDateTime(),
            )
        }
    }

    private fun sourceKey(commentId: Long) = "board-comment:$commentId"

    companion object {
        const val ROOT_PAGE_SIZE = 3
        const val REPLY_PAGE_SIZE = 5
    }
}
