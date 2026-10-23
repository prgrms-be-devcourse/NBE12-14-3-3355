package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportCreateRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportStatusUpdateRequest
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewReportGroupResponse
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewReportPageResponse
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewReportResponse
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus.APPROVED
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus.PENDING
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.repository.ReviewReportRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.util.Objects
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReviewReportService
constructor(
    private val changeRecorder: RelatedGameChangeRecorder,
    private val reviewReportRepository: ReviewReportRepository,
    private val reviewRepository: ReviewRepository,
    private val userRepository: UserRepository,
    private val userGameService: UserGameService,
) {
    @Transactional
    fun createReport(
        reporterId: Long,
        reviewId: Long,
        request: ReviewReportCreateRequest,
    ): ReviewReportResponse {
        val review = getReview(reviewId)
        val reporter = getUser(reporterId)
        // 본인이 작성한 리뷰는 신고할 수 없음.
        val reviewWriterId = review.userGame.user.id
        if (Objects.equals(reviewWriterId, reporterId)) {
            throw ServiceException("400-3", "본인이 작성한 리뷰는 신고할 수 없습니다.")
        }
        // 같은 사용자가 같은 리뷰를 여러 번 신고할 수 없음.
        if (reviewReportRepository.existsByReview_IdAndReporter_Id(reviewId, reporterId)) {
            throw ServiceException("409-1", "이미 신고한 리뷰입니다.")
        }
        val report = ReviewReport(review, reporter, request.reason)
        return ReviewReportResponse.from(reviewReportRepository.save(report))
    }

    fun getReports(
        status: ReportStatus?,
        pageable: Pageable,
    ): ReviewReportPageResponse {
        val groupPageable: Pageable =
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
        val reviewIdPage: Page<Long> =
            reviewReportRepository.findReviewIdsGroupedByReview(status, groupPageable)
        if (reviewIdPage.isEmpty()) {
            return ReviewReportPageResponse.from(reviewIdPage, emptyList())
        }
        val reports: List<ReviewReport> =
            reviewReportRepository.findAllByReviewIdsAndStatus(reviewIdPage.content, status)
        val reportsByReviewId = reports.groupBy { checkNotNull(it.review.id) }
        val groups = reviewIdPage.content.mapNotNull { reviewId ->
            reportsByReviewId[reviewId]?.let { ReviewReportGroupResponse.from(it) }
        }
        return ReviewReportPageResponse.from(reviewIdPage, groups)
    }

    @Transactional
    fun updateStatus(
        reportId: Long,
        request: ReviewReportStatusUpdateRequest,
    ): ReviewReportResponse {
        val report =
            reviewReportRepository
                .findById(reportId)
                .orElseThrow({ ServiceException("404-5", "리뷰 신고를 찾을 수 없습니다.") })
        if (request.status == ReportStatus.PENDING) {
            throw ServiceException("400-5", "신고 처리 결과는 승인 또는 반려여야 합니다.")
        }
        val pendingReports: List<ReviewReport> =
            reviewReportRepository.findByReview_IdAndStatus(checkNotNull(report.review.id), ReportStatus.PENDING)
        if (pendingReports.isEmpty()) {
            throw ServiceException("409-4", "이미 처리된 리뷰 신고입니다.")
        }
        if (request.status == ReportStatus.APPROVED) {
            report.review.hideByAdmin()
            changeRecorder.record(requireNotNull(report.review.userGame.game.id), RelatedGameChangeReason.REVIEW)
            userGameService.synchronizeLibraryState(report.review.userGame)
        }
        pendingReports.forEach({ pendingReport -> pendingReport.changeStatus(request.status) })
        return ReviewReportResponse.from(pendingReports.get(0))
    }

    private fun getReview(reviewId: Long): Review {
        return reviewRepository
            .findActiveById(reviewId)
            .orElseThrow({ ServiceException("404-3", "리뷰를 찾을 수 없습니다.") })
    }

    private fun getUser(userId: Long): User {
        return userRepository
            .findById(userId)
            .orElseThrow({ ServiceException("404-4", "사용자를 찾을 수 없습니다.") })
    }
}
