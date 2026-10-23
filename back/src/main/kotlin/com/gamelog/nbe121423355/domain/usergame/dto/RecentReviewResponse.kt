package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class RecentReviewResponse(
    val reviewId: Long,
    val gameId: Long,
    val gameTitle: String,
    val gameCoverImageUrl: String?,
    val playStatus: PlayStatus?,
    val platform: String?,
    val rating: BigDecimal?,
    val content: String?,
    val lastModifiedDate: LocalDateTime,
) {
    constructor(
        review: Review
    ) : this(
        checkNotNull(review.id) { "review.id must be present in a persisted response" },
        checkNotNull(review.userGame.game.id) { "review.userGame.game.id must be present in a persisted response" },
        checkNotNull(review.userGame.game.title) { "review.userGame.game.title must be present in a persisted response" },
        review.userGame.game.coverImageUrl,
        review.userGame.playStatus,
        review.userGame.platform?.name,
        review.rating,
        review.content,
        checkNotNull(review.lastModifiedDate) { "review.lastModifiedDate must be present in a persisted response" },
    )
}
