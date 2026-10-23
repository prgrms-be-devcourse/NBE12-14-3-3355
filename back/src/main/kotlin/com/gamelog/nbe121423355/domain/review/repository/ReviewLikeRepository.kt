package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.review.entity.ReviewLike
import com.gamelog.nbe121423355.domain.review.entity.ReviewLikeId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewLikeRepository : JpaRepository<ReviewLike, ReviewLikeId> {
    // 같은 사용자의 중복 좋아요 확인.
    fun existsByReview_IdAndUser_Id(reviewId: Long, userId: Long): Boolean

    // 리뷰의 좋아요 수 확인.
    fun countByReview_Id(reviewId: Long): Long

    @Query(
        """
            SELECT reviewLike.review.id AS reviewId, COUNT(reviewLike) AS likeCount
            FROM ReviewLike reviewLike
            WHERE reviewLike.review.id IN :reviewIds
            GROUP BY reviewLike.review.id

            """
    )
    fun findLikeCountsByReviewIds(
        @Param("reviewIds") reviewIds: Collection<Long>
    ): List<ReviewLikeCountProjection>

    @Query(
        """
            SELECT
                review.id AS reviewId,
                author.id AS userId,
                author.nickname AS nickname,
                author.profileImageUrl AS profileImageUrl,
                game.id AS gameId,
                game.title AS gameTitle,
                game.coverImageUrl AS gameCoverImageUrl,
                review.rating AS rating,
                review.content AS content,
                review.spoiler AS spoiler,
                COUNT(reviewLike) AS likeCount,
                review.createdDate AS createdDate
            FROM ReviewLike reviewLike
            JOIN reviewLike.review review
            JOIN review.userGame userGame
            JOIN userGame.user author
            JOIN userGame.game game
            WHERE review.content IS NOT NULL
              AND TRIM(review.content) <> ''
              AND (
                  review.status IS NULL
                  OR review.status = com.gamelog.nbe121423355.domain.review.entity.ReviewStatus.ACTIVE
              )
            GROUP BY
                review.id,
                author.id,
                author.nickname,
                author.profileImageUrl,
                game.id,
                game.title,
                game.coverImageUrl,
                review.rating,
                review.content,
                review.spoiler,
                review.createdDate
            ORDER BY
                COUNT(reviewLike) DESC,
                review.createdDate DESC,
                review.id DESC

            """
    )
    fun findPopularReviews(pageable: Pageable): List<PopularReviewProjection>

    @Query(
        """
            SELECT COUNT(reviewLike)
            FROM ReviewLike reviewLike
            WHERE reviewLike.review.userGame.user.id = :userId
              AND reviewLike.review.content IS NOT NULL
              AND TRIM(reviewLike.review.content) <> ''
              AND (
                  reviewLike.review.status IS NULL
                  OR reviewLike.review.status = com.gamelog.nbe121423355.domain.review.entity.ReviewStatus.ACTIVE
              )

            """
    )
    fun countVisibleLikesByUserId(@Param("userId") userId: Long): Long

    @Query(
        """
            SELECT COUNT(reviewLike)
            FROM ReviewLike reviewLike
            WHERE reviewLike.review.userGame.game.id = :gameId
              AND reviewLike.review.content IS NOT NULL
              AND TRIM(reviewLike.review.content) <> ''
              AND (
                  reviewLike.review.status IS NULL
                  OR reviewLike.review.status = com.gamelog.nbe121423355.domain.review.entity.ReviewStatus.ACTIVE
              )

            """
    )
    fun countVisibleLikesByGameId(@Param("gameId") gameId: Long): Long

    // 사용자가 누른 좋아요 취소.
    fun deleteByReview_IdAndUser_Id(reviewId: Long, userId: Long)
}
