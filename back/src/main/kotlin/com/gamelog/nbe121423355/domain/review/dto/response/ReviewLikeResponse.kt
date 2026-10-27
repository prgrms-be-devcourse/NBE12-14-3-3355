package com.gamelog.nbe121423355.domain.review.dto.response

@JvmRecord
data class ReviewLikeResponse(
    val reviewId: Long?,
    val likeCount: Long,
    val liked: Boolean,
) {}
