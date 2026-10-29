package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.review.entity.Review
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewRepository : JpaRepository<Review, Long> {
    // 신고 ID가 달라도 같은 리뷰의 일괄 처리는 직렬화한다. 엔티티를 미리 로드하지 않는다.
    @Query(
        value = """
            SELECT r.id FROM reviews r
            WHERE r.id = (SELECT report.review_id FROM review_reports report WHERE report.id = :reportId)
            FOR UPDATE
        """,
        nativeQuery = true,
    )
    fun lockReviewIdByReportId(@Param("reportId") reportId: Long): Long?

    @org.springframework.data.jpa.repository.Lock(
        jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
    )
    @Query("SELECT r FROM Review r WHERE r.id = :id AND " + ReviewRepository.ACTIVE_REVIEW)
    fun findActiveForUpdate(@Param("id") id: Long): Review?

    @Query(
        """
            SELECT r
            FROM Review r
            WHERE r.userGame.id = :userGameId
              AND
              """ +
            ReviewRepository.ACTIVE_REVIEW +
            """


            """
    )
    fun findByUserGame_Id(@Param("userGameId") userGameId: Long): Review?

    @Query(
        """
            SELECT r
            FROM Review r
            WHERE r.userGame.id = :userGameId

            """
    )
    fun findIncludingDeletedByUserGameId(@Param("userGameId") userGameId: Long): Review?

    @Query(
        """
            SELECT r
            FROM Review r
            WHERE r.id = :reviewId
              AND
              """ +
            ReviewRepository.ACTIVE_REVIEW +
            """


            """
    )
    fun findActiveById(@Param("reviewId") reviewId: Long): Review?

    @EntityGraph(attributePaths = ["userGame.user", "userGame.game", "userGame.platform"])
    @Query(
        """
            SELECT r
            FROM Review r
            WHERE r.userGame.game.id = :gameId
              AND (
                  r.rating IS NOT NULL
                  OR (
                      r.content IS NOT NULL
                      AND TRIM(r.content) <> ''
                  )
              )
              AND
              """ +
            ReviewRepository.ACTIVE_REVIEW +
            """


            """
    )
    fun findByUserGame_Game_Id(
        @Param("gameId") gameId: Long,
        pageable: Pageable,
    ): Page<Review>

    @EntityGraph(attributePaths = ["userGame.user", "userGame.game", "userGame.platform"])
    @Query(
        """
            SELECT r
            FROM Review r
            WHERE r.userGame.user.id = :userId
              AND (
                  r.rating IS NOT NULL
                  OR (
                      r.content IS NOT NULL
                      AND TRIM(r.content) <> ''
                  )
              )
              AND
              """ +
            ReviewRepository.ACTIVE_REVIEW +
            """


            """
    )
    fun findByUserGame_User_Id(
        @Param("userId") userId: Long,
        pageable: Pageable,
    ): Page<Review>

    @Query(
        """
            SELECT COALESCE(AVG(r.rating), 0)
            FROM Review r
            JOIN r.userGame ug
            WHERE ug.user.id = :userId
              AND ug.inLibrary = true
              AND r.rating IS NOT NULL
              AND
              """ +
            ReviewRepository.ACTIVE_REVIEW +
            """


            """
    )
    fun findAverageRating(@Param("userId") userId: Long): BigDecimal

    @Query(
        """
            SELECT r.rating
            FROM Review r
            JOIN r.userGame ug
            WHERE ug.user.id = :userId
              AND ug.inLibrary = true
              AND r.rating IS NOT NULL
              AND
              """ +
            ReviewRepository.ACTIVE_REVIEW +
            """


            """
    )
    fun findPlayedGameRatings(@Param("userId") userId: Long): MutableList<BigDecimal>

    @Query(
        """
    SELECT r
    FROM Review r
    JOIN FETCH r.userGame ug
    JOIN FETCH ug.game g
    LEFT JOIN FETCH ug.platform p
    WHERE ug.user.id = :userId
      AND (
          r.rating IS NOT NULL
          OR (
              r.content IS NOT NULL
              AND TRIM(r.content) <> ''
          )
      )
      AND
      """ +
            ReviewRepository.ACTIVE_REVIEW +
            """

    ORDER BY r.lastModifiedDate DESC

"""
    )
    fun findRecentReviews(
        @Param("userId") userId: Long,
        pageable: Pageable,
    ): MutableList<Review>

    // 회원탈퇴: 활성 리뷰만 숨김 (UPDATE에는 JOIN을 못 써서 user_game은 서브쿼리)
    // 벌크 UPDATE는 @Version을 자동 갱신하지 않으므로 이전 버전의 진행 중인 수정도 막는다.
    @Modifying(flushAutomatically = true)
    @Query(
        """
            UPDATE Review r
            SET r.status = com.gamelog.nbe121423355.domain.review.entity.ReviewStatus.DELETED_BY_USER,
                r.deletedAt = :now,
                r.version = r.version + 1
            WHERE r.userGame.id IN (SELECT ug.id FROM UserGame ug WHERE ug.user.id = :userId)
              AND
              """ +
            ReviewRepository.ACTIVE_REVIEW +
            """


            """
    )
    fun hideAllByUserId(
        @Param("userId") userId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    companion object {
        const val ACTIVE_REVIEW: String =
            "(r.status IS NULL OR r.status = com.gamelog.nbe121423355.domain.review.entity.ReviewStatus.ACTIVE)"
    }
}
