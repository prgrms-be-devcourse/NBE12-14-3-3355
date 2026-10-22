package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus

interface CommunityReviewProjection : PopularReviewProjection {
    val liked: Boolean
    val playStatus: PlayStatus?
    val playing: Boolean
    val backlog: Boolean
    val wishlist: Boolean
}
