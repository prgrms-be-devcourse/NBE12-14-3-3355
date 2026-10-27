package com.gamelog.nbe121423355.domain.board.repository

import com.gamelog.nbe121423355.domain.board.entity.BoardPostLike
import com.gamelog.nbe121423355.domain.board.entity.BoardPostLikeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BoardPostLikeRepository : JpaRepository<BoardPostLike, BoardPostLikeId> {
    interface LikeRow {
        val postId: Long
        val count: Long
        val liked: Long
    }

    @Query(
        """
        SELECT l.id.postId AS postId, COUNT(l) AS count,
               SUM(CASE WHEN l.id.userId = :viewerId THEN 1 ELSE 0 END) AS liked
        FROM BoardPostLike l
        WHERE l.id.postId IN :postIds
        GROUP BY l.id.postId
        """
    )
    fun counts(
        @Param("postIds") postIds: Collection<Long>,
        @Param("viewerId") viewerId: Long,
    ): List<LikeRow>

    @Modifying
    @Query("DELETE FROM BoardPostLike l WHERE l.id.postId = :postId")
    fun deleteAllByPostId(@Param("postId") postId: Long)
}
