package com.gamelog.nbe121423355.domain.game.sync

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IgdbSyncStore
constructor(private val repository: IgdbSyncRepository, private val catalog: IgdbSyncCatalog) {
    @Transactional(readOnly = true)
    fun status(): IgdbSyncState {
        return repository.findById(1L).orElseGet({ IgdbSyncState() })
    }

    @Transactional
    fun claim(userId: Long): IgdbSyncState {
        val state = repository.lockState().orElseGet({ repository.saveAndFlush(IgdbSyncState()) })
        val now = Instant.now().getEpochSecond()
        if ("RUNNING".equals(state.status) && state.leaseUntil > now)
            throw ServiceException(
                "409-1",
                "이미 동기화가 실행 중입니다.",
            )
        val resume = state.status in setOf("FAILED", "RUNNING", "STOPPED")
        if (!resume) {
            state.mode = if (state.watermark == null) "FULL" else "INCREMENTAL"
            state.sinceEpoch =
                if (state.watermark == null) null
                else Math.max(0, requireNotNull(state.watermark) - 300)
            state.cutoff = now
            state.skipped = 0
            state.deleted = state.skipped
            state.updated = state.deleted
            state.inserted = state.updated
            state.processed = state.inserted
            state.cursorId = state.processed
        }
        state.runId = UUID.randomUUID().toString()
        state.requestedBy = userId
        state.status = "RUNNING"
        state.error = null
        state.finishedAt = null
        state.leaseUntil = now + 600
        return state
    }

    @Transactional
    fun cancel(): IgdbSyncState {
        val state = repository.lockState().orElseGet { IgdbSyncState() }
        if (state.status == "RUNNING") {
            state.status = "STOPPED"
            state.error = null
            state.leaseUntil = 0
            state.finishedAt = Instant.now().epochSecond
        }
        return state
    }

    // 카탈로그 변경과 커서 저장을 같은 트랜잭션으로 처리합니다.
    @Transactional(timeout = 180)
    fun savePage(runId: String, page: MutableList<IgdbGameResponse>): Boolean {
        val state = repository.lockState().orElseThrow()
        if (!runId.equals(state.runId) || !"RUNNING".equals(state.status)) return false
        if (page.isEmpty()) {
            state.status = "SUCCEEDED"
            state.watermark = state.cutoff
            state.finishedAt = Instant.now().getEpochSecond()
            state.leaseUntil = 0
            return false
        }
        var cursor = state.cursorId
        for (game in page) {
            kotlin.check(!(game.id == null || game.id <= cursor)) { "잘못된 IGDB 페이지 순서" }
            cursor = game.id
        }
        val counts = catalog.apply(page, state.cutoff)
        state.processed += page.size
        state.inserted += counts.inserted
        state.updated += counts.updated
        state.deleted += counts.deleted
        state.skipped += counts.skipped
        state.cursorId = cursor
        state.leaseUntil = Instant.now().getEpochSecond() + 600
        return true
    }

    @Transactional
    fun fail(runId: String) {
        repository
            .lockState()
            .ifPresent({ state ->
                if (runId.equals(state.runId) && "RUNNING".equals(state.status)) {
                    state.status = "FAILED"
                    state.error = "동기화가 중단되었습니다. 서버 로그를 확인한 후 이어서 실행하세요."
                    state.leaseUntil = 0
                    state.finishedAt = Instant.now().getEpochSecond()
                }
            })
    }
}
