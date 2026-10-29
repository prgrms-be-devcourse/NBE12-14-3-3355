package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import java.time.LocalDateTime

data class ReviewReportItemResponse(
    val reportId: Long?,
    val reporterId: Long?,
    val reporterNickname: String?,
    val reason: String?,
    val status: ReportStatus?,
    val createdDate: LocalDateTime?,
    val lastModifiedDate: LocalDateTime?,
) {

    companion object {

        fun from(report: ReviewReport): ReviewReportItemResponse {
            return ReviewReportItemResponse(
                report.id,
                report.reporter.id,
                report.reporter.nickname,
                report.reason,
                report.status,
                report.createdDate,
                report.lastModifiedDate,
            )
        }
    }
}
