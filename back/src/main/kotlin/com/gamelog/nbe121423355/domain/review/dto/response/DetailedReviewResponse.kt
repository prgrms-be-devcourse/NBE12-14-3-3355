package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.usergame.dto.UserGameDto

@JvmRecord
data class DetailedReviewResponse(
    val userGame: UserGameDto?,
    val review: ReviewResponse?,
) {}
