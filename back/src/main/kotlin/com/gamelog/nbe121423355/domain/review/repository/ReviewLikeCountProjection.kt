package com.gamelog.nbe121423355.domain.review.repository

interface ReviewLikeCountProjection {
    val reviewId: Long?
    val likeCount: Long
}
