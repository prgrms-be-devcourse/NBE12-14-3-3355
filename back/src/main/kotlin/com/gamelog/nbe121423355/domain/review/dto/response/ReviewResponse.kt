package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@JvmRecord
data class ReviewResponse(
    val reviewId: Long?,
    val version: Long?,
    val userGameId: Long?,
    val gameId: Long?,
    val gameTitle: String?,
    val gameCoverImageUrl: String?,
    val gameReleaseDate: LocalDate?,
    val userId: Long?,
    val nickname: String?,
    val profileImageUrl: String?,
    val playStatus: PlayStatus?,
    val playing: Boolean,
    val backlog: Boolean,
    val wishlist: Boolean,
    val platformName: String?,
    val rating: BigDecimal?,
    val content: String?,
    val spoiler: Boolean,
    val likeCount: Long,
    val createdDate: LocalDateTime?,
    val lastModifiedDate: LocalDateTime?,
) {

    companion object {
        @JvmOverloads
        @JvmStatic
        fun from(review: Review, likeCount: Long = 0L): ReviewResponse {
            return ReviewResponse(
                review.id,
                review.version,
                review.userGame.id,
                review.userGame.game.id,
                review.userGame.game.title,
                review.userGame.game.coverImageUrl,
                review.userGame.game.releaseDate,
                review.userGame.user.id,
                review.userGame.user.nickname,
                review.userGame.user.profileImageUrl,
                review.userGame.playStatus,
                review.userGame.isPlaying,
                review.userGame.isBacklog,
                review.userGame.isWishlist,
                if (review.userGame.platform == null) null else review.userGame.platform?.name,
                review.rating,
                review.content,
                review.isSpoiler,
                likeCount,
                review.createdDate,
                review.lastModifiedDate,
            )
        }
    }
}
