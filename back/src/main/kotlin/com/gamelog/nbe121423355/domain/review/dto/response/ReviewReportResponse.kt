package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus.ACTIVE
import java.math.BigDecimal
import java.time.LocalDateTime

@JvmRecord
data class ReviewReportResponse(
    val reportId: Long?,
    val reviewId: Long?,
    val reporterId: Long?,
    val reporterNickname: String?,
    val reviewWriterId: Long?,
    val reviewWriterNickname: String?,
    val reason: String?,
    val reviewRating: BigDecimal?,
    val reviewContent: String?,
    val reviewSpoiler: Boolean,
    val reviewStatus: ReviewStatus?,
    val status: ReportStatus?,
    val createdDate: LocalDateTime?,
    val lastModifiedDate: LocalDateTime?,
) {

    companion object {
        @JvmStatic
        fun from(report: ReviewReport): ReviewReportResponse {
            return ReviewReportResponse(
                report.id,
                report.review.id,
                report.reporter.id,
                report.reporter.nickname,
                report.review.userGame.user.id,
                report.review.userGame.user.nickname,
                report.reason,
                if (report.reviewRatingSnapshot != null) report.reviewRatingSnapshot
                else report.review.rating,
                if (report.reviewContentSnapshot != null) report.reviewContentSnapshot
                else report.review.content,
                report.reviewSpoilerSnapshot ?: report.review.isSpoiler,
                if (report.review.status == null) ReviewStatus.ACTIVE else report.review.status,
                report.status,
                report.createdDate,
                report.lastModifiedDate,
            )
        }
    }
}
