package com.gamelog.nbe121423355.domain.board.service

import com.gamelog.nbe121423355.domain.board.dto.BoardReportPageResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardReportRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardReportResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardReportStatusRequest
import com.gamelog.nbe121423355.domain.board.entity.BoardReport
import com.gamelog.nbe121423355.domain.board.entity.BoardReportTarget
import com.gamelog.nbe121423355.domain.board.repository.BoardCommentRepository
import com.gamelog.nbe121423355.domain.board.repository.BoardPostRepository
import com.gamelog.nbe121423355.domain.board.repository.BoardReportRepository
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BoardReportService(
    private val postService: BoardPostService,
    private val commentService: BoardCommentService,
    private val posts: BoardPostRepository,
    private val comments: BoardCommentRepository,
    private val reports: BoardReportRepository,
) {
    @Transactional
    fun reportPost(viewerId: Long, postId: Long, request: BoardReportRequest): BoardReportResponse {
        val post = postService.lockVisible(postId)
        val report =
            save(
                targetType = BoardReportTarget.POST,
                targetId = postId,
                author = post.user,
                viewerId = viewerId,
                reason = request.reason,
                titleSnapshot = post.title,
                contentSnapshot = post.content,
                spoilerSnapshot = post.spoiler,
            )
        return BoardReportResponse.from(report)
    }

    @Transactional
    fun reportComment(viewerId: Long, postId: Long, commentId: Long, request: BoardReportRequest): BoardReportResponse {
        postService.lockVisible(postId)
        val comment = commentService.activeComment(postId, commentId)
        val report =
            save(
                targetType = BoardReportTarget.COMMENT,
                targetId = commentId,
                author = comment.user,
                viewerId = viewerId,
                reason = request.reason,
                titleSnapshot = null,
                contentSnapshot = comment.content,
                spoilerSnapshot = comment.spoiler,
            )
        return BoardReportResponse.from(report)
    }

    fun list(status: ReportStatus?, pageable: Pageable): BoardReportPageResponse =
        BoardReportPageResponse.from(reports.list(status, pageable))

    // 선택한 신고와 같은 대상의 대기 신고를 한꺼번에 처리한다
    @Transactional
    fun updateStatus(reportId: Long, request: BoardReportStatusRequest): BoardReportResponse {
        val report = reports.findByIdOrNull(reportId) ?: throw ServiceException("404-42", "신고를 찾을 수 없습니다.")
        val status = requireNotNull(request.status)
        if (status == ReportStatus.PENDING) {
            throw ServiceException("400-43", "신고 처리 결과는 승인 또는 반려여야 합니다.")
        }
        val pending =
            reports.findByTargetTypeAndTargetIdAndStatus(report.targetType, report.targetId, ReportStatus.PENDING)
        if (pending.isEmpty()) throw ServiceException("409-41", "이미 처리된 신고입니다.")
        if (status == ReportStatus.APPROVED) {
            when (report.targetType) {
                BoardReportTarget.POST -> posts.findByIdForUpdate(report.targetId)?.hideByAdmin()
                BoardReportTarget.COMMENT -> comments.findByIdOrNull(report.targetId)?.hideByAdmin()
            }
        }
        pending.forEach { it.changeStatus(status) }
        return BoardReportResponse.from(pending.first())
    }

    private fun save(
        targetType: BoardReportTarget,
        targetId: Long,
        author: User,
        viewerId: Long,
        reason: String?,
        titleSnapshot: String?,
        contentSnapshot: String,
        spoilerSnapshot: Boolean,
    ): BoardReport {
        val authorId = checkNotNull(author.id)
        if (authorId == viewerId) throw ServiceException("400-42", "본인이 작성한 글·댓글은 신고할 수 없습니다.")
        if (reports.existsByTargetTypeAndTargetIdAndReporter_Id(targetType, targetId, viewerId)) {
            throw ServiceException("409-40", "이미 신고했습니다.")
        }
        return reports.save(
            BoardReport(
                targetType = targetType,
                targetId = targetId,
                reporter = postService.activeUser(viewerId),
                reason = requireNotNull(reason).trim(),
                titleSnapshot = titleSnapshot,
                contentSnapshot = contentSnapshot,
                spoilerSnapshot = spoilerSnapshot,
                authorId = authorId,
            )
        )
    }
}
