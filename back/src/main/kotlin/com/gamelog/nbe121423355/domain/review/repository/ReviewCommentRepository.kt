package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.review.entity.ReviewComment
import java.util.*
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param

interface ReviewCommentRepository : JpaRepository<ReviewComment, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["user", "parent"])
    @org.springframework.data.jpa.repository.Query(
        """
        SELECT c FROM ReviewComment c WHERE c.review.id = :reviewId
        AND ((:parentId IS NULL AND c.parent IS NULL AND
             (c.deleted = false OR EXISTS (SELECT r FROM ReviewComment r WHERE r.parent = c AND r.deleted = false)))
          OR (:parentId IS NOT NULL AND c.parent.id = :parentId AND c.deleted = false))
        AND (:afterId IS NULL OR c.id > :afterId)
        ORDER BY c.id ASC

        """
    )
    fun list(
        @Param("reviewId") reviewId: Long?,
        @Param("parentId") parentId: Long?,
        @Param("afterId") afterId: Long?,
        pageable: Pageable?,
    ): MutableList<ReviewComment>

    fun countByReview_IdAndDeletedFalse(reviewId: Long?): Long

    interface ReplyCount {
        val parentId: Long?
        val count: Long
    }

    @org.springframework.data.jpa.repository.Query(
        "SELECT c.parent.id AS parentId, COUNT(c) AS count FROM ReviewComment c WHERE c.parent.id IN :ids AND c.deleted = false GROUP BY c.parent.id"
    )
    fun replyCounts(@Param("ids") ids: Collection<Long>?): MutableList<ReplyCount>

    @Modifying
    @org.springframework.data.jpa.repository.Query(
        "DELETE FROM ReviewComment c WHERE c.review.id = :reviewId AND c.parent IS NOT NULL"
    )
    fun deleteReplies(@Param("reviewId") reviewId: Long?)

    @Modifying
    @org.springframework.data.jpa.repository.Query(
        "DELETE FROM ReviewComment c WHERE c.review.id = :reviewId"
    )
    fun deleteForReview(@Param("reviewId") reviewId: Long?)
}
