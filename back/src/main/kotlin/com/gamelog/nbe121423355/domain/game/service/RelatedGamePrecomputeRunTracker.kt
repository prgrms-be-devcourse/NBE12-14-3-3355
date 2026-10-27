package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver.Progress
import java.time.Duration
import org.springframework.stereotype.Component

@Component
class RelatedGamePrecomputeRunTracker(private val store: RelatedGamePrecomputeRunStore) {
    fun observer(runId: String): RelatedGamePrecomputeObserver =
        observer(runId, System::nanoTime)

    fun observer(runId: String, nanoTime: () -> Long): RelatedGamePrecomputeObserver =
        object : RelatedGamePrecomputeObserver {
            private var latest = Progress(0, 0, 0)
            private var savedProcessed = 0L
            private var savedAt = nanoTime()

            override fun onAcquired() = store.start(runId)

            override fun onBaselineRegistered() = store.baselineRegistered(runId)

            override fun onPrepared(upperId: Long?, estimatedTotal: Long) {
                store.prepared(runId, upperId, estimatedTotal)
                savedAt = nanoTime()
            }

            override fun onProcessed(gameId: Long, progress: Progress, failure: RuntimeException?) {
                val now = nanoTime()
                val due = progress.processed - savedProcessed >= 100 ||
                    now - savedAt >= Duration.ofSeconds(5).toNanos()
                if (failure != null) {
                    // 실패 상세 저장이 롤백되면 실패 집계도 앞서 나가지 않게 한다.
                    store.failure(runId, gameId, progress, failure)
                    latest = progress
                } else if (due) {
                    latest = progress
                    store.progress(runId, progress)
                } else {
                    latest = progress
                    return
                }
                savedProcessed = progress.processed
                savedAt = now
            }

            override fun onCompleted(result: RelatedGamePrecomputeBatchService.Result) {
                store.complete(runId, Progress(
                    result.processed, result.succeeded, result.failedIds.size.toLong(),
                ))
            }

            override fun onRejected() = store.reject(runId)

            override fun onFailed(failure: RuntimeException) = store.fail(runId, latest, failure)
        }
}
