package com.gamelog.nbe121423355.domain.review.dto.response

data class ReviewLikeResponse(
    val reviewId: Long?,
    val likeCount: Long,
    val liked: Boolean,
)
