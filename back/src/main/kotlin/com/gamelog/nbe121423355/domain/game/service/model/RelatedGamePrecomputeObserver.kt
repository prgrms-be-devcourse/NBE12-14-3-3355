package com.gamelog.nbe121423355.domain.game.service.model

import com.gamelog.nbe121423355.domain.game.service.RelatedGamePrecomputeBatchService

interface RelatedGamePrecomputeObserver {
    fun onAcquired() {}

    fun onPrepared(upperId: Long?, estimatedTotal: Long) {}

    fun onProcessed(
        gameId: Long,
        progress: Progress,
        failure: RuntimeException?,
    ) {}

    fun onCompleted(result: RelatedGamePrecomputeBatchService.Result) {}

    fun onRejected() {}

    fun onFailed(failure: RuntimeException) {}

    @JvmRecord
    data class Progress(val processed: Long, val succeeded: Long, val failed: Long) {
        init {
            require(processed >= 0 && succeeded >= 0 && failed >= 0)
            require(processed == succeeded + failed)
        }
    }

    companion object {
        // CLI·신규 게임 처리는 관리자 실행 이력을 생성하지 않는다.
        @JvmField
        val NONE: RelatedGamePrecomputeObserver = object : RelatedGamePrecomputeObserver {}
    }
}
