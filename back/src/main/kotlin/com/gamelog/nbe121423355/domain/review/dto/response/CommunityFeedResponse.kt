package com.gamelog.nbe121423355.domain.review.dto.response

@JvmRecord
data class CommunityFeedResponse(
    val reviews: MutableList<CommunityReviewResponse>,
    val hasNext: Boolean,
    val hasFollowing: Boolean,
) {}
