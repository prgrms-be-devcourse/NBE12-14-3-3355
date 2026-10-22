package com.gamelog.nbe121423355.domain.review.dto.response

import org.springframework.data.domain.Page

@JvmRecord
data class ReviewReportPageResponse(
    val reports: MutableList<ReviewReportGroupResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {

    companion object {
        @JvmStatic
        fun from(
            reviewIdPage: Page<Long>,
            reports: MutableList<ReviewReportGroupResponse>,
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
