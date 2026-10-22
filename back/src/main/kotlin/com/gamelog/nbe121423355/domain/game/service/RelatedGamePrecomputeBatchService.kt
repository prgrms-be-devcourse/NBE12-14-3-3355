package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot
import com.gamelog.nbe121423355.global.interop.javaNonNull
import java.time.Duration
import java.util.ArrayList
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(propagation = NOT_SUPPORTED)
class RelatedGamePrecomputeBatchService
constructor(
    private val gameRepository: GameRepository,
    private val precomputeService: RelatedGamePrecomputeService,
    private val executionLock: RelatedGamePrecomputeLock,
) {
    fun run(
        all: Boolean,
        gameIds: MutableList<Long>,
        mode: RelatedGamePrecomputeMode?,
    ): com.gamelog.nbe121423355.domain.game.service.RelatedGamePrecomputeBatchService.Result {
        javaNonNull(gameIds, "gameIds")
        javaNonNull(mode, "mode")
        kotlin.require(all != !gameIds.isEmpty()) { "전체 실행과 지정 ID 중 하나를 선택해야 합니다." }
        val targets: MutableList<Long> =
            gameIds
                .stream()
                .map({ id ->
                    kotlin.require(!(id == null || id < 1)) { "게임 ID는 양수여야 합니다." }
                    id
                })
                .distinct()
                .sorted()
                .toList()
        val startedAt: Long = System.nanoTime()
        // 실행 도중 추가되는 게임 때문에 전체 작업이 계속 늘어나지 않게 경계를 고정한다.
        return executionLock
            .tryRun(
                java.util.function.Function { checkOwnership ->
                    val progress = Progress()
                    if (all) {
                        val upperId = gameRepository.findMaxGameIdForPrecompute()
                        val preparationStartedAt: Long = System.nanoTime()
                        val snapshot: RelatedGamePrecomputeSnapshot =
                            precomputeService.prepare(mode, upperId)
                        RelatedGamePrecomputeBatchService.log.info(
                            "추천 공통 데이터 준비: 기준 게임={}, 적격 후보={}, 행동 기준 게임={}, 소요시간={}ms",
                            snapshot.sourceCount(),
                            snapshot.candidateCount(),
                            snapshot.interactionSourceCount(),
                            Duration.ofNanos(System.nanoTime() - preparationStartedAt).toMillis(),
                        )
                        var afterId: Long = 0L
                        while (true) {
                            checkOwnership.run()
                            val page: MutableList<Long> =
                                gameRepository.findGameIdsForPrecompute(
                                    afterId,
                                    upperId,
                                    PageRequest.of(0, RelatedGamePrecomputeBatchService.PAGE_SIZE),
                                )
                            if (page.isEmpty()) {
                                break
                            }
                            process(page, mode, snapshot, checkOwnership, progress)
                            afterId = page.last()
                        }
                        RelatedGamePrecomputeBatchService.log.info(
                            "장르 조합별 기본 추천 계산: {}회",
                            snapshot.calculatedGroupCount(),
                        )
                    } else {
                        process(targets, mode, null, checkOwnership, progress)
                    }
                    val result =
                        com.gamelog.nbe121423355.domain.game.service
                            .RelatedGamePrecomputeBatchService
                            .Result(
                                true,
                                progress.processed,
                                progress.succeeded,
                                java.util.List.copyOf(progress.failedIds),
                                Duration.ofNanos(System.nanoTime() - startedAt),
                            )
                    RelatedGamePrecomputeBatchService.log.info(
                        "추천 계산 완료: 처리={}, 성공={}, 실패={}, 소요시간={}초, 실패 ID={}",
                        result.processed,
                        result.succeeded,
                        result.failedIds.size,
                        result.elapsed.toSeconds(),
                        result.failedIds,
                    )
                    result
                }
            )
            .orElseGet({
                RelatedGamePrecomputeBatchService.log.warn("추천 계산이 이미 실행 중입니다. 추가 작업을 시작하지 않습니다.")
                com.gamelog.nbe121423355.domain.game.service.RelatedGamePrecomputeBatchService
                    .Result(
                        false,
                        0,
                        0,
                        java.util.List.of(),
                        Duration.ofNanos(System.nanoTime() - startedAt),
                    )
            })
    }

    private fun process(
        ids: MutableList<Long>,
        mode: RelatedGamePrecomputeMode?,
        snapshot: RelatedGamePrecomputeSnapshot?,
        checkOwnership: Runnable,
        progress: Progress,
    ) {
        for (gameId in ids) {
            checkOwnership.run()
            try {
                if (snapshot == null) {
                    precomputeService.precompute(gameId, mode)
                } else {
                    precomputeService.precompute(gameId, mode, snapshot)
                }
                progress.succeeded++
            } catch (failure: RuntimeException) {
                progress.failedIds.add(gameId)
                RelatedGamePrecomputeBatchService.log.error(
                    "추천 계산 실패: gameId={}, error={}",
                    gameId,
                    failure.toString(),
                )
            }
            progress.processed++
            // 연결 단절은 개별 게임 실패로 삼키지 않고 전체 실행을 중단한다.
            checkOwnership.run()
            if (progress.processed % RelatedGamePrecomputeBatchService.PAGE_SIZE == 0L) {
                RelatedGamePrecomputeBatchService.log.info(
                    "추천 계산 진행: 처리={}, 성공={}, 실패={}",
                    progress.processed,
                    progress.succeeded,
                    progress.failedIds.size,
                )
            }
        }
    }

    private class Progress {
        var processed: Long = 0L
        var succeeded: Long = 0L
        val failedIds: MutableList<Long> = ArrayList()
    }

    @JvmRecord
    data class Result(
        val acquired: Boolean,
        val processed: Long,
        val succeeded: Long,
        val failedIds: MutableList<Long>,
        val elapsed: Duration,
    ) {
        fun exitCode(): Int {
            return if (!acquired) 2 else if (failedIds.isEmpty()) 0 else 1
        }
    }

    companion object {
        private val log: org.slf4j.Logger =
            org.slf4j.LoggerFactory.getLogger(RelatedGamePrecomputeBatchService::class.java)
        private const val PAGE_SIZE = 100
    }
}
