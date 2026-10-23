package com.gamelog.nbe121423355.domain.game.entity

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver.Progress
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

enum class RelatedGamePrecomputeRunStatus {
    ACCEPTED, PREPARING, RUNNING,
    SUCCEEDED, PARTIAL_FAILED, FAILED, INTERRUPTED, REJECTED;

    fun isTerminal(): Boolean = this != ACCEPTED && this != PREPARING && this != RUNNING
}

/** 게임별 최신 계산 상태와 별도로 보존하는 관리자 실행 이력. */
@Entity
@Table(
    name = "related_game_precompute_runs",
    indexes = [
        Index(name = "idx_precompute_run_requested", columnList = "requested_at, id"),
        Index(name = "idx_precompute_run_status", columnList = "status, heartbeat_at"),
    ],
)
class RelatedGamePrecomputeRun protected constructor() {
    @Id
    @Column(nullable = false, length = 36)
    var id: String = UUID.randomUUID().toString()
        protected set

    @Version
    var version: Long? = null
        protected set

    @Column(name = "requested_by", nullable = false)
    var requestedBy: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "target_type", nullable = false, length = 20)
    lateinit var targetType: RelatedGamePrecomputeTargetType
        protected set

    @Column(name = "requested_game_ids", columnDefinition = "TEXT")
    var requestedGameIds: String? = null
        protected set

    @Column(name = "source_run_id", length = 36)
    var sourceRunId: String? = null
        protected set

    @Column(name = "owner_id", nullable = false, length = 36)
    lateinit var ownerId: String
        protected set

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var status: RelatedGamePrecomputeRunStatus = RelatedGamePrecomputeRunStatus.ACCEPTED
        protected set

    @Column(name = "upper_game_id")
    var upperGameId: Long? = null
        protected set

    @Column(name = "estimated_total")
    var estimatedTotal: Long? = null
        protected set

    @Column(nullable = false)
    var processed: Long = 0
        protected set

    @Column(nullable = false)
    var succeeded: Long = 0
        protected set

    @Column(nullable = false)
    var failed: Long = 0
        protected set

    @Column(name = "requested_at", nullable = false)
    lateinit var requestedAt: LocalDateTime
        protected set

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null
        protected set

    @Column(name = "finished_at")
    var finishedAt: LocalDateTime? = null
        protected set

    @Column(name = "progress_updated_at")
    var progressUpdatedAt: LocalDateTime? = null
        protected set

    @Column(name = "heartbeat_at", nullable = false)
    lateinit var heartbeatAt: LocalDateTime
        protected set

    @Column(length = 500)
    var error: String? = null
        protected set

    constructor(
        requestedBy: Long,
        target: RelatedGamePrecomputeTarget,
        ownerId: String,
        sourceRunId: String?,
        now: LocalDateTime,
    ) : this() {
        require(requestedBy > 0)
        require(ownerId.isNotBlank() && ownerId.length <= 36)
        require(sourceRunId == null || sourceRunId.length <= 36)
        val ids = target.normalizedIds()
        require(ids.size <= 1_000) { "관리자 지정 ID는 최대 1,000개입니다." }
        this.requestedBy = requestedBy
        this.targetType = target.type
        this.requestedGameIds = ids.takeIf { it.isNotEmpty() }?.joinToString(",")
        this.sourceRunId = sourceRunId
        this.ownerId = ownerId
        this.requestedAt = now
        this.heartbeatAt = now
    }

    fun target(): RelatedGamePrecomputeTarget = RelatedGamePrecomputeTarget(
        targetType,
        requestedGameIds?.split(',')?.map(String::toLong) ?: emptyList(),
    )

    fun start(now: LocalDateTime) {
        check(status == RelatedGamePrecomputeRunStatus.ACCEPTED)
        status = RelatedGamePrecomputeRunStatus.PREPARING
        startedAt = now
        heartbeatAt = now
    }

    fun prepared(upperId: Long?, total: Long, now: LocalDateTime) {
        check(status == RelatedGamePrecomputeRunStatus.PREPARING)
        require(total >= 0)
        upperGameId = upperId
        estimatedTotal = total
        status = RelatedGamePrecomputeRunStatus.RUNNING
        record(Progress(0, 0, 0), now)
    }

    fun record(progress: Progress, now: LocalDateTime) {
        check(status == RelatedGamePrecomputeRunStatus.RUNNING)
        require(progress.processed >= processed)
        require(progress.succeeded >= succeeded && progress.failed >= failed)
        processed = progress.processed
        succeeded = progress.succeeded
        failed = progress.failed
        progressUpdatedAt = now
    }

    fun complete(progress: Progress, now: LocalDateTime) {
        record(progress, now)
        terminate(
            if (failed == 0L) RelatedGamePrecomputeRunStatus.SUCCEEDED
            else RelatedGamePrecomputeRunStatus.PARTIAL_FAILED,
            null,
            now,
        )
    }

    fun terminate(next: RelatedGamePrecomputeRunStatus, message: String?, now: LocalDateTime) {
        check(!status.isTerminal())
        require(next.isTerminal())
        status = next
        error = message?.take(500)
        finishedAt = now
    }

    fun heartbeat(owner: String, now: LocalDateTime) {
        check(ownerId == owner && !status.isTerminal())
        heartbeatAt = now
    }
}
