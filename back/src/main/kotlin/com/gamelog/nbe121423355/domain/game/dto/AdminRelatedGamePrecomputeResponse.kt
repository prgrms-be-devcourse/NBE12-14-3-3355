package com.gamelog.nbe121423355.domain.game.dto

import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import java.time.Duration
import java.time.LocalDateTime

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
                runId = run.id,
                requestedBy = run.requestedBy,
                type = run.targetType,
                gameIds = run.target().gameIds,
                sourceRunId = run.sourceRunId,
                status = run.status,
                upperGameId = run.upperGameId,
                estimatedTotal = run.estimatedTotal,
                processed = run.processed,
                succeeded = run.succeeded,
                failed = run.failed,
                requestedAt = run.requestedAt,
                startedAt = run.startedAt,
                finishedAt = run.finishedAt,
                progressUpdatedAt = run.progressUpdatedAt,
                heartbeatAt = run.heartbeatAt,
                heartbeatExpired = !run.status.isTerminal() && !run.heartbeatAt.isAfter(now.minus(staleAfter)),
                elapsedSeconds = Duration.between(run.startedAt ?: run.requestedAt, run.finishedAt ?: now)
                    .seconds.coerceAtLeast(0),
                error = run.error,
                initializeBaseline = run.initializeBaseline,
                baselineRegisteredAt = run.baselineRegisteredAt,
            )
    }
}

data class AdminRelatedGamePrecomputeFailureResponse(
    val gameId: Long,
    val error: String,
    val occurredAt: LocalDateTime,
)

data class AdminRelatedGamePrecomputePage<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class AdminRelatedGamePrecomputeStatusResponse(
    val databaseLocked: Boolean,
    val localRequestActive: Boolean,
    val activeRuns: List<AdminRelatedGamePrecomputeRunResponse>,
    // 잠금이 사용 중이면 생존 신호가 오래돼도 중단을 확정하지 않는다.
    val recoveryDeferred: Boolean,
)
