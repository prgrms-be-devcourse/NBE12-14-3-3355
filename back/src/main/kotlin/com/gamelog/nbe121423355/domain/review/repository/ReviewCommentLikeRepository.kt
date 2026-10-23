package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.review.entity.*
import java.util.*
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param

interface ReviewCommentLikeRepository : JpaRepository<ReviewCommentLike, ReviewCommentLikeId> {
    interface Likes {
        val commentId: Long?
        val count: Long
        val liked: Boolean
    }

    @org.springframework.data.jpa.repository.Query(
        """
        SELECT l.reviewComment.id AS commentId, COUNT(l) AS count,
          CASE WHEN SUM(CASE WHEN l.user.id = :viewerId THEN 1 ELSE 0 END) > 0 THEN true ELSE false END AS liked
        FROM ReviewCommentLike l WHERE l.reviewComment.id IN :ids GROUP BY l.reviewComment.id

        """
    )
    fun counts(
        @Param("ids") ids: Collection<Long>,
        @Param("viewerId") viewerId: Long?,
    ): List<Likes>

    @Modifying
    @org.springframework.data.jpa.repository.Query(
        "DELETE FROM ReviewCommentLike l WHERE l.reviewComment.id = :id"
    )
    fun deleteForComment(@Param("id") id: Long)

    @Modifying
    @org.springframework.data.jpa.repository.Query(
        "DELETE FROM ReviewCommentLike l WHERE l.reviewComment.review.id = :id"
    )
    fun deleteForReview(@Param("id") id: Long)
}
