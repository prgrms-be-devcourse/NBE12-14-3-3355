package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus
import java.time.LocalDateTime
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RelatedGamePrecomputeRunRepository : JpaRepository<RelatedGamePrecomputeRun, String> {
    // 집계·생존 신호의 동시 갱신을 직렬화한다. 계산 작업의 GET_LOCK과는 별개다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT run FROM RelatedGamePrecomputeRun run WHERE run.id = :runId")
    fun lockById(@Param("runId") runId: String): RelatedGamePrecomputeRun?

    fun findByStatusInOrderByRequestedAtDescIdDesc(
        statuses: Collection<RelatedGamePrecomputeRunStatus>,
        pageable: Pageable,
    ): List<RelatedGamePrecomputeRun>

    fun existsByStatusInAndHeartbeatAtAfter(
        statuses: Collection<RelatedGamePrecomputeRunStatus>,
        cutoff: LocalDateTime,
    ): Boolean

    @Query("""
        SELECT run.id FROM RelatedGamePrecomputeRun run
        WHERE run.status IN :statuses AND run.heartbeatAt <= :cutoff
        ORDER BY run.heartbeatAt, run.id
    """)
    fun findStaleIds(
        @Param("statuses") statuses: Collection<RelatedGamePrecomputeRunStatus>,
        @Param("cutoff") cutoff: LocalDateTime,
        pageable: Pageable,
    ): List<String>
}
