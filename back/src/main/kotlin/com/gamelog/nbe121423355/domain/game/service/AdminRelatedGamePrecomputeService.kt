package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.*
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeTargetRepository
import com.gamelog.nbe121423355.domain.game.service.model.*
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver.Progress
import com.gamelog.nbe121423355.global.exception.ServiceException
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AdminRelatedGamePrecomputeService(
    private val targets: RelatedGamePrecomputeTargetRepository,
    private val store: RelatedGamePrecomputeRunStore,
    private val query: RelatedGamePrecomputeRunQueryService,
    private val batch: RelatedGamePrecomputeBatchService,
    private val tracker: RelatedGamePrecomputeRunTracker,
    private val executionLock: RelatedGamePrecomputeLock,
    private val recovery: RelatedGamePrecomputeRecoveryService,
    private val properties: RelatedGamePrecomputeAdminProperties,
) {
    private val ownerId = UUID.randomUUID().toString()
    private val busy = AtomicBoolean()
    private val closing = AtomicBoolean()
    private val activeRun = AtomicReference<String?>()
    // 대기열 없이 한 요청만 처리한다. 다른 서버·CLI와의 경쟁은 기존 GET_LOCK이 결정한다.
    private val worker = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, SynchronousQueue<Runnable>(),
        Thread.ofPlatform().daemon().name("related-precompute-worker-", 0).factory(),
        ThreadPoolExecutor.AbortPolicy(),
    )
    // 긴 게임 계산과 복구 점검이 생존 신호 전송을 막지 않도록 별도 스레드를 둔다.
    private val maintenance = ScheduledThreadPoolExecutor(
        2, Thread.ofPlatform().daemon().name("related-precompute-maintenance-", 0).factory(),
    )

    @PostConstruct
    fun scheduleMaintenance() {
        maintenance.scheduleWithFixedDelay(
            { safelyMaintain { sendHeartbeat() } },
            properties.heartbeatInterval.toMillis(), properties.heartbeatInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
        maintenance.scheduleWithFixedDelay(
            { safelyMaintain { recoverIfIdle() } },
            properties.recoveryInterval.toMillis(), properties.recoveryInterval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    fun start(requestedBy: Long, request: AdminRelatedGamePrecomputeRequest): AdminRelatedGamePrecomputeRunResponse {
        val target = validate(request)
        recoverIfIdle()
        if (closing.get() || !busy.compareAndSet(false, true)) throw conflict()
        var runId: String? = null
        try {
            if (executionLock.isLocked() || query.hasRecentActiveRun()) throw conflict()
            runId = store.create(requestedBy, target, ownerId, request.sourceRunId)
            // worker가 빨리 끝나도 접수 응답의 의미는 일관되게 유지한다.
            val accepted = query.detail(runId)
            activeRun.set(runId)
            val id = runId
            worker.execute { execute(id, target) }
            return accepted
        } catch (failure: RuntimeException) {
            if (runId != null) {
                try {
                    if (failure is RejectedExecutionException) store.reject(runId)
                    else store.fail(runId, RelatedGamePrecomputeObserver.Progress(0, 0, 0), failure)
                } catch (recordingFailure: RuntimeException) {
                    if (failure !== recordingFailure) failure.addSuppressed(recordingFailure)
                }
            }
            activeRun.compareAndSet(runId, null)
            busy.set(false)
            if (failure is RejectedExecutionException) throw conflict()
            throw failure
        }
    }

    fun status(): AdminRelatedGamePrecomputeStatusResponse {
        recoverIfIdle()
        return query.status(executionLock.isLocked(), busy.get())
    }

    fun history(page: Int, size: Int) = query.history(page, size)
    fun detail(runId: String) = query.detail(runId)
    fun failures(runId: String, page: Int, size: Int) = query.failures(runId, page, size)

    private fun validate(request: AdminRelatedGamePrecomputeRequest): RelatedGamePrecomputeTarget {
        val type = request.type ?: throw badRequest("실행 종류를 선택하세요.")
        val text = request.gameIds.orEmpty()
        if (text.length > 30_000) throw badRequest("게임 ID 입력이 너무 깁니다.")
        val ids = if (type == RelatedGamePrecomputeTargetType.IDS) {
            if (text.isBlank()) throw badRequest("게임 ID를 입력하세요.")
            val tokens = text.trim().split(Regex("[,\\s]+"))
            val invalid = tokens.filter { it.toLongOrNull()?.let { id -> id > 0 } != true }
            if (invalid.isNotEmpty()) throw badRequest("잘못된 게임 ID: ${invalid.take(20).joinToString(", ").take(500)}")
            val parsed = tokens.map(String::toLong).distinct().sorted()
            if (parsed.size > 1_000) throw badRequest("관리자 지정 ID는 최대 1,000개입니다.")
            val missing = parsed - targets.findExistingIds(parsed).toSet()
            if (missing.isNotEmpty()) throw badRequest("존재하지 않는 게임 ID: ${missing.take(50).joinToString(", ")}")
            parsed
        } else {
            if (text.isNotBlank()) throw badRequest("지정 ID 실행에만 게임 ID를 입력해야 합니다.")
            emptyList()
        }
        if (request.initializeBaseline && type != RelatedGamePrecomputeTargetType.ALL) {
            throw badRequest("초기 기준선은 전체 갱신에서만 설정할 수 있습니다.")
        }
        val target = RelatedGamePrecomputeTarget(type, ids, request.initializeBaseline)
        request.sourceRunId?.let { sourceId ->
            val original = query.replayTarget(sourceId)
            if (original.type != type || original.normalizedIds() != target.normalizedIds() || original.initializeBaseline != target.initializeBaseline) {
                throw badRequest("원본 실행과 같은 종류·지정 ID로 다시 요청해야 합니다.")
            }
        }
        return target
    }

    private fun execute(runId: String, target: RelatedGamePrecomputeTarget) {
        val delegate = tracker.observer(runId)
        val observer = object : RelatedGamePrecomputeObserver {
            override fun onAcquired() { checkActive(); delegate.onAcquired() }
            override fun onBaselineRegistered() { checkActive(); delegate.onBaselineRegistered() }
            override fun onPrepared(upperId: Long?, estimatedTotal: Long) {
                checkActive(); delegate.onPrepared(upperId, estimatedTotal)
            }
            override fun onProcessed(gameId: Long, progress: Progress, failure: RuntimeException?) {
                checkActive(); delegate.onProcessed(gameId, progress, failure)
            }
            override fun onCompleted(result: RelatedGamePrecomputeBatchService.Result) {
                checkActive(); delegate.onCompleted(result)
            }
            override fun onRejected() = delegate.onRejected()
            override fun onFailed(failure: RuntimeException) {
                if (closing.get() || Thread.currentThread().isInterrupted) store.interruptOwned(runId, ownerId)
                else delegate.onFailed(failure)
            }
        }
        try {
            batch.run(target, RelatedGamePrecomputeMode.REFRESH, observer)
        } catch (failure: RuntimeException) {
            log.error("관리자 추천 갱신 실행 종료: runId={}", runId, failure)
        } finally {
            activeRun.compareAndSet(runId, null)
            busy.set(false)
        }
    }

    private fun checkActive() {
        if (closing.get() || Thread.currentThread().isInterrupted) throw CancellationException("추천 갱신 worker 종료")
    }

    private fun sendHeartbeat() {
        activeRun.get()?.let { store.heartbeat(it, ownerId) }
    }

    private fun recoverIfIdle() {
        if (!busy.get() && !closing.get()) recovery.recover()
    }

    private fun safelyMaintain(action: () -> Unit) {
        try { action() } catch (failure: RuntimeException) {
            log.warn("추천 실행 상태 유지 실패", failure)
        }
    }

    @PreDestroy
    fun stop() {
        if (!closing.compareAndSet(false, true)) return
        maintenance.shutdownNow()
        worker.shutdownNow()
    }

    private fun conflict() = ServiceException("409-10", "추천 갱신이 실행 중이거나 접수되어 있습니다.")
    private fun badRequest(message: String) = ServiceException("400-10", message)

    companion object {
        private val log = LoggerFactory.getLogger(AdminRelatedGamePrecomputeService::class.java)
    }
}
