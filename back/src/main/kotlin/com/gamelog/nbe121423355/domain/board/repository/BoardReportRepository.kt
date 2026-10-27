package com.gamelog.nbe121423355.domain.board.repository

import com.gamelog.nbe121423355.domain.board.entity.BoardReport
import com.gamelog.nbe121423355.domain.board.entity.BoardReportTarget
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BoardReportRepository : JpaRepository<BoardReport, Long> {
    fun existsByTargetTypeAndTargetIdAndReporter_Id(
        targetType: BoardReportTarget,
        targetId: Long,
        reporterId: Long,
    ): Boolean

    fun findByTargetTypeAndTargetIdAndStatus(
        targetType: BoardReportTarget,
        targetId: Long,
        status: ReportStatus,
    ): List<BoardReport>

    @EntityGraph(attributePaths = ["reporter"])
    @Query("SELECT r FROM BoardReport r WHERE (:status IS NULL OR r.status = :status) ORDER BY r.id DESC")
    fun list(@Param("status") status: ReportStatus?, pageable: Pageable): Page<BoardReport>
}
