package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeTargetRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import com.gamelog.nbe121423355.global.interop.javaNonNull
import java.time.Duration
import java.util.function.Function
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(propagation = NOT_SUPPORTED)
class RelatedGamePrecomputeBatchService(
    private val gameRepository: GameRepository,
    private val precomputeService: RelatedGamePrecomputeService,
    private val executionLock: RelatedGamePrecomputeLock,
    private val changePreparation: RelatedGameChangePreparation,
    private val changeExpansion: RelatedGameChangeExpansion,
    private val refreshTargets: com.gamelog.nbe121423355.domain.game.repository.RelatedGameRefreshTargetRepository,
    private val targetRepository: RelatedGamePrecomputeTargetRepository,
) {
    // 기존 CLI·신규 게임 호출 계약을 유지한다.
    fun run(all: Boolean, gameIds: MutableList<Long>, mode: RelatedGamePrecomputeMode?): Result {
        javaNonNull(gameIds, "gameIds")
        javaNonNull(mode, "mode")
        require(all != gameIds.isNotEmpty()) { "전체 실행과 지정 ID 중 하나를 선택해야 합니다." }
        return run(
            RelatedGamePrecomputeTarget(
                if (all) RelatedGamePrecomputeTargetType.ALL else RelatedGamePrecomputeTargetType.IDS,
                gameIds.toList(),
            ),
            requireNotNull(mode),
            RelatedGamePrecomputeObserver.NONE,
        )
    }

    fun run(
        target: RelatedGamePrecomputeTarget,
        mode: RelatedGamePrecomputeMode,
        observer: RelatedGamePrecomputeObserver,
    ): Result {
        val startedAt = System.nanoTime()
        try {
            // 실행 도중 추가되는 게임 때문에 전체 작업이 계속 늘어나지 않게 경계를 고정한다.
            val result = executionLock.tryRun(Function<Runnable, Result> { checkOwnership ->
                observer.onAcquired()
                val progress = Progress()
                if (target.type == RelatedGamePrecomputeTargetType.ALL || target.type == RelatedGamePrecomputeTargetType.CHANGED) {
                    changePreparation.expandPending(checkOwnership)
                }
                when (target.type) {
                    RelatedGamePrecomputeTargetType.ALL -> {
                        val upperId = gameRepository.findMaxGameIdForPrecompute()
                        if (target.initializeBaseline) {
                            checkOwnership.run()
                            changeExpansion.initializeBaseline(upperId)
                            observer.onBaselineRegistered()
                        }
                        val preparationStartedAt = System.nanoTime()
                        val snapshot = precomputeService.prepare(mode, upperId)
                        log.info(
                            "추천 공통 데이터 준비: 기준 게임={}, 적격 후보={}, 행동 기준 게임={}, 소요시간={}ms",
                            snapshot.sourceCount(), snapshot.candidateCount(),
                            snapshot.interactionSourceCount(),
                            Duration.ofNanos(System.nanoTime() - preparationStartedAt).toMillis(),
                        )
                        observer.onPrepared(upperId, snapshot.sourceCount().toLong())
                        processPages(upperId, mode, snapshot, checkOwnership, progress, observer) { afterId ->
                            gameRepository.findGameIdsForPrecompute(
                                afterId, upperId, PageRequest.of(0, PAGE_SIZE),
                            )
                        }
                        log.info("장르 조합별 기본 추천 계산: {}회", snapshot.calculatedGroupCount())
                    }
                    RelatedGamePrecomputeTargetType.CHANGED -> {
                        val upperId = gameRepository.findMaxGameIdForPrecompute()
                        observer.onPrepared(upperId, refreshTargets.countPending(upperId))
                        processPages(upperId, mode, null, checkOwnership, progress, observer) { afterId ->
                            refreshTargets.findPendingIds(afterId, upperId, PAGE_SIZE)
                        }
                    }
                    RelatedGamePrecomputeTargetType.IDS -> {
                        val ids = target.normalizedIds()
                        observer.onPrepared(null, ids.size.toLong())
                        process(ids, mode, null, checkOwnership, progress, observer)
                    }
                    RelatedGamePrecomputeTargetType.FAILED,
                    RelatedGamePrecomputeTargetType.UNREADY -> {
                        val upperId = gameRepository.findMaxGameIdForPrecompute()
                        observer.onPrepared(upperId, targetRepository.count(target.type, upperId))
                        processPages(upperId, mode, null, checkOwnership, progress, observer) { afterId ->
                            targetRepository.findIds(target.type, afterId, upperId, PAGE_SIZE)
                        }
                    }
                }
                Result(
                    true, progress.processed, progress.succeeded,
                    java.util.List.copyOf(progress.failedIds),
                    Duration.ofNanos(System.nanoTime() - startedAt),
                )
            }).orElseGet {
                log.warn("추천 계산이 이미 실행 중입니다. 추가 작업을 시작하지 않습니다.")
                Result(false, 0, 0, java.util.List.of(), Duration.ofNanos(System.nanoTime() - startedAt))
            }
            // 잠금 해제까지 성공한 뒤 종료 결과를 기록한다.
            if (result.acquired) {
                observer.onCompleted(result)
                log.info(
                    "추천 계산 완료: 처리={}, 성공={}, 실패={}, 소요시간={}초, 실패 ID={}",
                    result.processed, result.succeeded, result.failedIds.size,
                    result.elapsed.toSeconds(), result.failedIds,
                )
            } else {
                observer.onRejected()
            }
            return result
        } catch (failure: RuntimeException) {
            try {
                observer.onFailed(failure)
            } catch (recordingFailure: RuntimeException) {
                // 실패 기록도 불가능한 경우 원래 실행 예외를 잃지 않는다.
                if (recordingFailure !== failure) failure.addSuppressed(recordingFailure)
            }
            throw failure
        }
    }

    private fun processPages(
        upperId: Long,
        mode: RelatedGamePrecomputeMode,
        snapshot: RelatedGamePrecomputeSnapshot?,
        checkOwnership: Runnable,
        progress: Progress,
        observer: RelatedGamePrecomputeObserver,
        findPage: (Long) -> List<Long>,
    ) {
        var afterId = 0L
        while (true) {
            checkOwnership.run()
            val page = findPage(afterId)
            if (page.isEmpty()) return
            check(page.first() > afterId && page.last() <= upperId)
            process(page, mode, snapshot, checkOwnership, progress, observer)
            afterId = page.last()
        }
    }

    private fun process(
        ids: List<Long>,
        mode: RelatedGamePrecomputeMode,
        snapshot: RelatedGamePrecomputeSnapshot?,
        checkOwnership: Runnable,
        progress: Progress,
        observer: RelatedGamePrecomputeObserver,
    ) {
        for (gameId in ids) {
            checkOwnership.run()
            var gameFailure: RuntimeException? = null
            try {
                if (snapshot == null) {
                    precomputeService.precompute(gameId, mode)
                } else {
                    precomputeService.precompute(gameId, mode, snapshot)
                }
                progress.succeeded++
            } catch (failure: RuntimeException) {
                gameFailure = failure
                progress.failedIds.add(gameId)
                log.error("추천 계산 실패: gameId={}, error={}", gameId, failure.toString())
            }
            progress.processed++
            // 이력 저장 실패는 게임 계산 실패로 삼키지 않고 전체 실행을 중단한다.
            observer.onProcessed(gameId, progress.snapshot(), gameFailure)
            // 연결 단절은 개별 게임 실패로 삼키지 않고 전체 실행을 중단한다.
            checkOwnership.run()
            if (progress.processed % PAGE_SIZE == 0L) {
                log.info(
                    "추천 계산 진행: 처리={}, 성공={}, 실패={}",
                    progress.processed, progress.succeeded, progress.failedIds.size,
                )
            }
        }
    }

    private class Progress {
        var processed = 0L
        var succeeded = 0L
        val failedIds: MutableList<Long> = ArrayList()

        fun snapshot() = RelatedGamePrecomputeObserver.Progress(
            processed, succeeded, failedIds.size.toLong(),
        )
    }

    @JvmRecord
    data class Result(
        val acquired: Boolean,
        val processed: Long,
        val succeeded: Long,
        val failedIds: MutableList<Long>,
        val elapsed: Duration,
    ) {
        fun exitCode(): Int = if (!acquired) 2 else if (failedIds.isEmpty()) 0 else 1
    }

    companion object {
        private val log = LoggerFactory.getLogger(RelatedGamePrecomputeBatchService::class.java)
        private const val PAGE_SIZE = 100
    }
}
