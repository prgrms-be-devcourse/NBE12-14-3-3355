package com.gamelog.nbe121423355.domain.review.repository

import java.math.BigDecimal
import java.time.LocalDateTime

interface PopularReviewProjection {
    val reviewId: Long?

    val userId: Long?

    val nickname: String?

    val profileImageUrl: String?

    val gameId: Long?

    val gameTitle: String?

    val gameCoverImageUrl: String?

    val rating: BigDecimal?

    val content: String?

    val spoiler: Boolean

    val likeCount: Long

    val createdDate: LocalDateTime?
}
