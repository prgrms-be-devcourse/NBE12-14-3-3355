package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunFailure
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunFailureRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver.Progress
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(propagation = REQUIRES_NEW)
class RelatedGamePrecomputeRunStore(
    private val runRepository: RelatedGamePrecomputeRunRepository,
    private val failureRepository: RelatedGamePrecomputeRunFailureRepository,
) {
    @JvmOverloads
    fun create(
        requestedBy: Long,
        target: RelatedGamePrecomputeTarget,
        ownerId: String,
        sourceRunId: String? = null,
    ): String = runRepository.saveAndFlush(
        RelatedGamePrecomputeRun(requestedBy, target, ownerId, sourceRunId, LocalDateTime.now())
    ).id

    fun start(runId: String) {
        lock(runId).start(LocalDateTime.now())
    }

    fun prepared(runId: String, upperId: Long?, total: Long) {
        lock(runId).prepared(upperId, total, LocalDateTime.now())
    }

    fun progress(runId: String, progress: Progress) {
        lock(runId).record(progress, LocalDateTime.now())
    }

    fun failure(runId: String, gameId: Long, progress: Progress, failure: RuntimeException) {
        val run = lock(runId)
        val now = LocalDateTime.now()
        // 집계와 실패 상세는 함께 커밋하거나 함께 롤백한다.
        run.record(progress, now)
        failureRepository.save(RelatedGamePrecomputeRunFailure(
            run, gameId, summary(failure), now,
        ))
    }

    fun complete(runId: String, progress: Progress) {
        lock(runId).complete(progress, LocalDateTime.now())
    }

    fun reject(runId: String) {
        val run = lock(runId)
        check(run.status == RelatedGamePrecomputeRunStatus.ACCEPTED)
        run.terminate(
            RelatedGamePrecomputeRunStatus.REJECTED,
            "다른 추천 계산이 실행 중이어서 시작하지 않았습니다.",
            LocalDateTime.now(),
        )
    }

    fun fail(runId: String, progress: Progress, failure: RuntimeException) {
        val run = lock(runId)
        if (run.status.isTerminal()) return
        val now = LocalDateTime.now()
        if (run.status == RelatedGamePrecomputeRunStatus.RUNNING) run.record(progress, now)
        run.terminate(RelatedGamePrecomputeRunStatus.FAILED, summary(failure), now)
    }

    // 다음 단계의 worker 생존 신호에서 호출한다. 집계 저장 시각과 구분한다.
    fun heartbeat(runId: String, ownerId: String) {
        val run = lock(runId)
        if (run.status.isTerminal() || run.ownerId != ownerId) return
        run.heartbeat(ownerId, LocalDateTime.now())
    }

    // 호출자가 공통 실행 잠금을 보유한 복구 구간에서만 사용한다.
    fun interruptStale(runId: String, cutoff: LocalDateTime): Boolean {
        val run = runRepository.lockById(runId) ?: return false
        // 대상 조회 이후 생존 신호나 종료 상태가 갱신됐으면 변경하지 않는다.
        if (run.status.isTerminal() || run.heartbeatAt.isAfter(cutoff)) return false
        interrupt(run)
        return true
    }

    fun interruptOwned(runId: String, ownerId: String) {
        val run = lock(runId)
        if (run.status.isTerminal() || run.ownerId != ownerId) return
        interrupt(run)
    }

    private fun interrupt(run: RelatedGamePrecomputeRun) {
        run.terminate(
            RelatedGamePrecomputeRunStatus.INTERRUPTED,
            "실행이 중단되었습니다. 마지막 저장 집계를 확인하고 새 실행을 요청하세요.",
            LocalDateTime.now(),
        )
    }

    private fun lock(runId: String): RelatedGamePrecomputeRun =
        checkNotNull(runRepository.lockById(runId)) { "추천 갱신 실행 이력이 없습니다." }

    private fun summary(failure: RuntimeException): String =
        "${failure.javaClass.simpleName.take(200)}: 작업에 실패했습니다. 서버 로그를 확인하세요."
}
