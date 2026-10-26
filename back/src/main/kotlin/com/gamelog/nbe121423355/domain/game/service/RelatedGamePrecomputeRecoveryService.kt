package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeAdminProperties
import java.time.LocalDateTime
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class RelatedGamePrecomputeRecoveryService(
    private val runs: RelatedGamePrecomputeRunRepository,
    private val store: RelatedGamePrecomputeRunStore,
    private val executionLock: RelatedGamePrecomputeLock,
    private val properties: RelatedGamePrecomputeAdminProperties,
) {
    fun recover(): Int {
        val cutoff = LocalDateTime.now().minus(properties.staleAfter)
        val ids = runs.findStaleIds(
            RelatedGamePrecomputeRunQueryService.ACTIVE_STATUSES, cutoff, PageRequest.of(0, 100),
        )
        if (ids.isEmpty()) return 0
        // 조회만으로 판단하지 않는다. CLI·다른 서버의 계산이 있으면 정리를 보류한다.
        return executionLock.tryRun { checkOwnership ->
            var interrupted = 0
            for (id in ids) {
                checkOwnership()
                if (store.interruptStale(id, cutoff)) interrupted++
            }
            checkOwnership()
            interrupted
        } ?: 0
    }
}
