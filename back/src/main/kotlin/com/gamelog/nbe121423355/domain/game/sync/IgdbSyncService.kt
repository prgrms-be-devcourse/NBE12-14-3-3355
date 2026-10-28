package com.gamelog.nbe121423355.domain.game.sync

import com.gamelog.nbe121423355.domain.game.client.IgdbClient
import jakarta.annotation.PreDestroy
import java.util.concurrent.*
import org.springframework.stereotype.Service

@Service
class IgdbSyncService
constructor(private val store: IgdbSyncStore, private val client: IgdbClient) {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    fun start(userId: Long): IgdbSyncState {
        val state = store.claim(userId)
        try {
            worker.submit({ run(state) })
        } catch (e: RuntimeException) {
            store.fail(requireNotNull(state.runId))
            throw e
        }
        return state
    }

    private fun run(state: IgdbSyncState) {
        try {
            var cursor = state.cursorId
            while (!Thread.currentThread().isInterrupted()) {
                val current = store.status()
                if (current.runId != state.runId || current.status != "RUNNING") return
                val page = client.fetchPage(cursor, state.sinceEpoch, state.cutoff)
                if (!store.savePage(requireNotNull(state.runId), page)) return
                cursor = requireNotNull(page.last().id)
            }
            store.fail(requireNotNull(state.runId))
        } catch (e: Exception) {
            IgdbSyncService.log.error("IGDB 동기화 실패 runId={}", state.runId, e)
            store.fail(requireNotNull(state.runId))
        }
    }

    @PreDestroy
    fun stop() {
        worker.shutdownNow()
    }

    companion object {
        private val log: org.slf4j.Logger =
            org.slf4j.LoggerFactory.getLogger(IgdbSyncService::class.java)
    }
}
