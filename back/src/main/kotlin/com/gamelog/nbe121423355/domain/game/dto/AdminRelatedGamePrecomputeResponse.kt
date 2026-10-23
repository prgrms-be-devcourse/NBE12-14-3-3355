package com.gamelog.nbe121423355.domain.game.dto

import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import java.time.Duration
import java.time.LocalDateTime

@JvmRecord
data class AdminRelatedGamePrecomputeRunResponse(
    val runId: String,
    val requestedBy: Long,
    val type: RelatedGamePrecomputeTargetType,
    val gameIds: List<Long>,
    val sourceRunId: String?,
    val status: RelatedGamePrecomputeRunStatus,
    val upperGameId: Long?,
    val estimatedTotal: Long?,
    val processed: Long,
    val succeeded: Long,
    val failed: Long,
    val requestedAt: LocalDateTime,
    val startedAt: LocalDateTime?,
    val finishedAt: LocalDateTime?,
    val progressUpdatedAt: LocalDateTime?,
    val heartbeatAt: LocalDateTime,
    val heartbeatExpired: Boolean,
    val elapsedSeconds: Long,
    val error: String?,
    val initializeBaseline: Boolean,
    val baselineRegisteredAt: LocalDateTime?,
) {
    companion object {
        fun from(run: RelatedGamePrecomputeRun, now: LocalDateTime, staleAfter: Duration) =
            AdminRelatedGamePrecomputeRunResponse(
                run.id, run.requestedBy, run.targetType, run.target().gameIds,
                run.sourceRunId, run.status, run.upperGameId, run.estimatedTotal,
                run.processed, run.succeeded, run.failed, run.requestedAt, run.startedAt,
                run.finishedAt, run.progressUpdatedAt, run.heartbeatAt,
                !run.status.isTerminal() && !run.heartbeatAt.isAfter(now.minus(staleAfter)),
                Duration.between(run.startedAt ?: run.requestedAt, run.finishedAt ?: now)
                    .seconds.coerceAtLeast(0),
                run.error, run.initializeBaseline, run.baselineRegisteredAt,
            )
    }
}

@JvmRecord
data class AdminRelatedGamePrecomputeFailureResponse(
    val gameId: Long,
    val error: String,
    val occurredAt: LocalDateTime,
)

@JvmRecord
data class AdminRelatedGamePrecomputePage<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

@JvmRecord
data class AdminRelatedGamePrecomputeStatusResponse(
    val databaseLocked: Boolean,
    val localRequestActive: Boolean,
    val activeRuns: List<AdminRelatedGamePrecomputeRunResponse>,
    // 잠금이 사용 중이면 생존 신호가 오래돼도 중단을 확정하지 않는다.
    val recoveryDeferred: Boolean,
)
