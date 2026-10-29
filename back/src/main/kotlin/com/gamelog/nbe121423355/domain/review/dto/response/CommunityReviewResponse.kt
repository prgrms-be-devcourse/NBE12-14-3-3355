package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.review.repository.CommunityReviewProjection
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class CommunityReviewResponse(
    val reviewId: Long?,
    val userId: Long?,
    val nickname: String?,
    val profileImageUrl: String?,
    val gameId: Long?,
    val gameTitle: String?,
    val gameCoverImageUrl: String?,
    val rating: BigDecimal?,
    val content: String?,
    val spoiler: Boolean,
    val likeCount: Long,
    val createdDate: LocalDateTime?,
    val liked: Boolean,
    val playStatus: PlayStatus?,
    val playing: Boolean,
    val backlog: Boolean,
    val wishlist: Boolean,
) {

    companion object {

        fun from(r: CommunityReviewProjection): CommunityReviewResponse {
            return CommunityReviewResponse(
                r.reviewId,
                r.userId,
                r.nickname,
                r.profileImageUrl,
                r.gameId,
                r.gameTitle,
                r.gameCoverImageUrl,
                r.rating,
                r.content,
                r.spoiler,
                r.likeCount,
                r.createdDate,
                r.liked,
                r.playStatus,
                r.playing,
                r.backlog,
                r.wishlist,
            )
        }
    }
}
