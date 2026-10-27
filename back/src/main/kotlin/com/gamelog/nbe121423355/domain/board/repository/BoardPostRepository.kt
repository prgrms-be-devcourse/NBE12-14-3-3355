package com.gamelog.nbe121423355.domain.board.repository

import com.gamelog.nbe121423355.domain.board.entity.BoardPost
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BoardPostRepository : JpaRepository<BoardPost, Long> {
    @Query(
        """
        SELECT p FROM BoardPost p
        JOIN FETCH p.user u
        LEFT JOIN FETCH p.game
        WHERE p.id = :id AND $VISIBLE
        """
    )
    fun findVisibleById(@Param("id") id: Long): BoardPost?

    // 작성자 행까지 잠그지 않도록 조인 없이 게시글만 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM BoardPost p WHERE p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): BoardPost?

    @Query(
        """
        SELECT p FROM BoardPost p
        JOIN FETCH p.user u
        LEFT JOIN FETCH p.game
        WHERE $VISIBLE
          AND (:afterId IS NULL OR p.id < :afterId)
          AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%')
               OR p.content LIKE CONCAT('%', :keyword, '%'))
        ORDER BY p.id DESC
        """
    )
    fun list(
        @Param("keyword") keyword: String?,
        @Param("afterId") afterId: Long?,
        pageable: Pageable,
    ): List<BoardPost>

    companion object {
        // 탈퇴한 사용자의 글은 상태를 바꾸지 않고 조회에서 제외한다
        const val VISIBLE =
            "p.status = com.gamelog.nbe121423355.domain.board.entity.BoardPostStatus.ACTIVE AND u.deletedAt IS NULL"
    }
}
