package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@JvmRecord
data class ReviewResponse(
    val reviewId: Long?,
    @field:Schema(description = "이 리뷰의 현재 버전. 기존 리뷰 수정 시 요청 본문의 version, 삭제 시 쿼리 파라미터 version으로 전달합니다. 성공 응답의 새 버전을 사용하고, 409-6 충돌 시 최신 리뷰를 재조회합니다.", example = "0")
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
