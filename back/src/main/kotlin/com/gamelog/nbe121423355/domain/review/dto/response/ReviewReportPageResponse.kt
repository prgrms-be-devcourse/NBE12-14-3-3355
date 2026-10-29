package com.gamelog.nbe121423355.domain.review.dto.response

import org.springframework.data.domain.Page

data class ReviewReportPageResponse(
    val reports: List<ReviewReportGroupResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {

    companion object {

        fun from(
            reviewIdPage: Page<Long>,
            reports: List<ReviewReportGroupResponse>,
        ): ReviewReportPageResponse {
            return ReviewReportPageResponse(
                reports,
                reviewIdPage.getNumber(),
                reviewIdPage.size,
                reviewIdPage.totalElements,
                reviewIdPage.totalPages,
                reviewIdPage.hasNext(),
            )
        }
    }
}
