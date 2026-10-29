package com.gamelog.nbe121423355.domain.review.dto.response

data class CommunityFeedResponse(
    val reviews: List<CommunityReviewResponse>,
    val hasNext: Boolean,
    val hasFollowing: Boolean,
)
