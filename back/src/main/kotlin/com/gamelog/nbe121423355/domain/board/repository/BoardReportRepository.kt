package com.gamelog.nbe121423355.domain.board.repository

import com.gamelog.nbe121423355.domain.board.entity.BoardReport
import com.gamelog.nbe121423355.domain.board.entity.BoardReportTarget
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BoardReportRepository : JpaRepository<BoardReport, Long> {
    fun existsByTargetTypeAndTargetIdAndReporter_Id(
        targetType: BoardReportTarget,
        targetId: Long,
        reporterId: Long,
    ): Boolean

    // 스냅샷이 아닌 최신 커밋 기준으로 읽어야 승인 대기 중에 접수된 신고까지 함께 처리된다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTargetTypeAndTargetIdAndStatus(
        targetType: BoardReportTarget,
        targetId: Long,
        status: ReportStatus,
    ): List<BoardReport>

    @EntityGraph(attributePaths = ["reporter"])
    @Query("SELECT r FROM BoardReport r WHERE (:status IS NULL OR r.status = :status) ORDER BY r.id DESC")
    fun list(@Param("status") status: ReportStatus?, pageable: Pageable): Page<BoardReport>
}
