package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.review.entity.Review
import org.springframework.data.domain.Page

@JvmRecord
data class ReviewPageResponse(
    val reviews: List<ReviewResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val totalLikes: Long,
) {

    companion object {
        @JvmStatic
        fun from(
            reviewPage: Page<Review>,
            likeCounts: Map<Long, Long>,
            totalLikes: Long,
        ): ReviewPageResponse {
            val reviews = reviewPage.content.map { review ->
                ReviewResponse.from(review, likeCounts[review.id] ?: 0L)
            }

            return ReviewPageResponse(
                reviews,
                reviewPage.getNumber(),
                reviewPage.size,
                reviewPage.totalElements,
                reviewPage.totalPages,
                reviewPage.hasNext(),
                totalLikes,
            )
        }
    }
}
