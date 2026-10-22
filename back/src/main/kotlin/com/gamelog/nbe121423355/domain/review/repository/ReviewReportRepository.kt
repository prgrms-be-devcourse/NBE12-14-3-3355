package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewReportRepository : JpaRepository<ReviewReport, Long> {
    // 같은 사용자의 중복 신고 확인.
    fun existsByReview_IdAndReporter_Id(reviewId: Long?, reporterId: Long?): Boolean

    // 처리 상태별 신고 목록 조회.
    @EntityGraph(attributePaths = ["reporter", "review.userGame.user"])
    fun findByStatus(status: ReportStatus?, pageable: Pageable?): Page<ReviewReport>

    // 관리자 목록에서 신고자와 리뷰 작성자 닉네임을 추가 쿼리 없이 조회.
    @EntityGraph(attributePaths = ["reporter", "review.userGame.user"])
    override fun findAll(pageable: Pageable): Page<ReviewReport>

    // 관리자 목록의 페이지 기준을 신고 건수가 아닌 신고 대상 리뷰로 잡는다.
    @Query(
        value =
            """
            SELECT report.review.id
            FROM ReviewReport report
            WHERE (:status IS NULL OR report.status = :status)
            GROUP BY report.review.id
            ORDER BY MAX(report.createdDate) DESC

            """,
        countQuery =
            """
            SELECT COUNT(DISTINCT report.review.id)
            FROM ReviewReport report
            WHERE (:status IS NULL OR report.status = :status)

            """,
    )
    fun findReviewIdsGroupedByReview(
        @Param("status") status: ReportStatus?,
        pageable: Pageable?,
    ): Page<Long>

    @EntityGraph(attributePaths = ["reporter", "review.userGame.user", "review.userGame.game"])
    @Query(
        """
            SELECT report
            FROM ReviewReport report
            WHERE report.review.id IN :reviewIds
              AND (:status IS NULL OR report.status = :status)
            ORDER BY report.createdDate DESC

            """
    )
    fun findAllByReviewIdsAndStatus(
        @Param("reviewIds") reviewIds: MutableList<Long>,
        @Param("status") status: ReportStatus?,
    ): MutableList<ReviewReport>

    @EntityGraph(attributePaths = ["reporter", "review.userGame.user"])
    fun findByReview_IdAndStatus(reviewId: Long?, status: ReportStatus?): MutableList<ReviewReport>
}
