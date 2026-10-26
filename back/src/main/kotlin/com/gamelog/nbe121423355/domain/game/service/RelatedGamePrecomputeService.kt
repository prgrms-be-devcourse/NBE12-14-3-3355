package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeSnapshotRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode.REFRESH
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot
import com.gamelog.nbe121423355.global.interop.javaNonNull
import java.time.LocalDateTime
import java.util.function.Supplier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(propagation = NOT_SUPPORTED)
class RelatedGamePrecomputeService
constructor(
    private val queryRepository: RelatedGameQueryRepository,
    private val refreshTargets: com.gamelog.nbe121423355.domain.game.repository.RelatedGameRefreshTargetRepository,
    private val store: RelatedGamePrecomputeStore,
    private val snapshotRepository: RelatedGamePrecomputeSnapshotRepository,
) {
    fun prepare(mode: RelatedGamePrecomputeMode?, upperId: Long): RelatedGamePrecomputeSnapshot {
        return snapshotRepository.load(
            javaNonNull(mode, "mode"),
            upperId,
            RelatedGamePrecomputeService.CANDIDATE_LIMIT,
        )
    }

    // 외부 실행은 단일 실행 잠금을 담당하는 배치 서비스를 통해 호출한다.
    fun precompute(gameId: Long, mode: RelatedGamePrecomputeMode?): Int {
        javaNonNull(gameId, "gameId")
        javaNonNull(mode, "mode")
        return calculateAndReplace(
            gameId,
            Supplier {
                queryRepository.findCandidatesForPrecompute(
                    gameId,
                    RelatedGamePrecomputeService.CANDIDATE_LIMIT,
                    mode == RelatedGamePrecomputeMode.REFRESH,
                )
            },
        )
    }

    fun precompute(
        gameId: Long,
        mode: RelatedGamePrecomputeMode?,
        snapshot: RelatedGamePrecomputeSnapshot,
    ): Int {
        javaNonNull(gameId, "gameId")
        javaNonNull(snapshot, "snapshot")
        kotlin.require(snapshot.mode() == javaNonNull(mode, "mode")) { "계산 모드와 공통 데이터의 모드가 다릅니다." }
        return calculateAndReplace(
            gameId,
            Supplier {
                snapshot
                    .baselineFor(gameId)
                    .orElseGet({
                        queryRepository.findCandidatesForPrecompute(
                            gameId,
                            RelatedGamePrecomputeService.CANDIDATE_LIMIT,
                            mode == RelatedGamePrecomputeMode.REFRESH,
                        )
                    })
            },
        )
    }

    private fun calculateAndReplace(
        gameId: Long,
        calculate: Supplier<MutableList<RelatedGameCandidateProjection>>,
    ): Int {
        // 요구 버전은 배치의 준비 단계 이후 증가하지 않는다. 이후 원천 변경은 다음 실행에 남는다.
        val capturedVersion = refreshTargets.findByGameId(gameId)?.requiredVersion
        val attemptedAt: LocalDateTime? = LocalDateTime.now()
        store.start(gameId, attemptedAt)
        try {
            val candidates: MutableList<RelatedGameCandidateProjection> = calculate.get()
            store.replace(gameId, candidates, LocalDateTime.now(), capturedVersion)
            return candidates.size
        } catch (failure: RuntimeException) {
            try {
                store.fail(
                    gameId,
                    attemptedAt,
                    failure.javaClass.getSimpleName() + ": " + failure.message,
                )
            } catch (recordingFailure: RuntimeException) {
                // 실패 기록도 불가능한 경우 원래 계산·저장 예외를 잃지 않는다.
                failure.addSuppressed(recordingFailure)
            }
            throw failure
        }
    }

    companion object {
        // 사용자별 제외 후 상위 5개를 확보하기 위해 기준 게임별 연관 후보를 최대 20개 저장 (이후 변경 가능)
        private const val CANDIDATE_LIMIT = 20
    }
}
