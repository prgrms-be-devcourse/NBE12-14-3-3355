package com.gamelog.nbe121423355.domain.board.repository

import com.gamelog.nbe121423355.domain.board.entity.BoardComment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BoardCommentRepository : JpaRepository<BoardComment, Long> {
    interface CountRow {
        val id: Long
        val count: Long
    }

    // 지워진 원댓글은 남은 답글이 있을 때만 자리를 유지한다
    @EntityGraph(attributePaths = ["user"])
    @Query(
        """
        SELECT c FROM BoardComment c
        WHERE c.post.id = :postId
          AND ((:parentId IS NULL AND c.parent IS NULL AND
                ((c.deleted = false AND c.hidden = false) OR EXISTS (
                    SELECT r FROM BoardComment r WHERE r.parent = c AND r.deleted = false AND r.hidden = false)))
            OR (:parentId IS NOT NULL AND c.parent.id = :parentId AND c.deleted = false AND c.hidden = false))
          AND (:afterId IS NULL OR c.id > :afterId)
        ORDER BY c.id ASC
        """
    )
    fun list(
        @Param("postId") postId: Long,
        @Param("parentId") parentId: Long?,
        @Param("afterId") afterId: Long?,
        pageable: Pageable,
    ): List<BoardComment>

    @Query(
        """
        SELECT c.post.id AS id, COUNT(c) AS count FROM BoardComment c
        WHERE c.post.id IN :postIds AND c.deleted = false AND c.hidden = false
        GROUP BY c.post.id
        """
    )
    fun countsByPost(@Param("postIds") postIds: Collection<Long>): List<CountRow>

    @Query(
        """
        SELECT c.parent.id AS id, COUNT(c) AS count FROM BoardComment c
        WHERE c.parent.id IN :parentIds AND c.deleted = false AND c.hidden = false
        GROUP BY c.parent.id
        """
    )
    fun replyCounts(@Param("parentIds") parentIds: Collection<Long>): List<CountRow>

    fun countByPost_IdAndDeletedFalseAndHiddenFalse(postId: Long): Long

    @Query("SELECT c.id FROM BoardComment c WHERE c.post.id = :postId")
    fun findIdsByPostId(@Param("postId") postId: Long): List<Long>
}
