package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.*
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunFailureRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeRunRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeAdminProperties
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RelatedGamePrecomputeRunQueryService(
    private val runs: RelatedGamePrecomputeRunRepository,
    private val failures: RelatedGamePrecomputeRunFailureRepository,
    private val properties: RelatedGamePrecomputeAdminProperties,
) {
    fun detail(runId: String): AdminRelatedGamePrecomputeRunResponse = response(load(runId))

    fun replayTarget(runId: String): RelatedGamePrecomputeTarget {
        val run = load(runId)
        if (!run.status.isTerminal()) throw ServiceException("409-10", "종료된 실행만 다시 요청할 수 있습니다.")
        return run.target()
    }

    fun history(page: Int, size: Int): AdminRelatedGamePrecomputePage<AdminRelatedGamePrecomputeRunResponse> {
        val result = runs.findAll(paging(page, size, Sort.by(
            Sort.Order.desc("requestedAt"), Sort.Order.desc("id"),
        )))
        return AdminRelatedGamePrecomputePage(
            content = result.content.map(::response),
            page = page,
            size = size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    fun failures(runId: String, page: Int, size: Int): AdminRelatedGamePrecomputePage<AdminRelatedGamePrecomputeFailureResponse> {
        load(runId)
        val result = failures.findByRun_IdOrderByGameIdAsc(runId, paging(page, size))
        return AdminRelatedGamePrecomputePage(
            content = result.content.map { failure ->
                AdminRelatedGamePrecomputeFailureResponse(
                    gameId = failure.gameId,
                    error = failure.error,
                    occurredAt = failure.occurredAt,
                )
            },
            page = page,
            size = size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    fun hasRecentActiveRun(): Boolean = runs.existsByStatusInAndHeartbeatAtAfter(
        ACTIVE_STATUSES, LocalDateTime.now().minus(properties.staleAfter),
    )

    fun status(databaseLocked: Boolean, localRequestActive: Boolean): AdminRelatedGamePrecomputeStatusResponse {
        val active = runs.findByStatusInOrderByRequestedAtDescIdDesc(ACTIVE_STATUSES, PageRequest.of(0, 20))
            .map(::response)
        return AdminRelatedGamePrecomputeStatusResponse(
            databaseLocked = databaseLocked,
            localRequestActive = localRequestActive,
            activeRuns = active,
            recoveryDeferred = databaseLocked && active.any { it.heartbeatExpired },
        )
    }

    private fun load(runId: String): RelatedGamePrecomputeRun = runs.findByIdOrNull(runId)
        ?: throw ServiceException("404-10", "추천 갱신 실행 이력이 없습니다.")

    private fun response(run: RelatedGamePrecomputeRun) =
        AdminRelatedGamePrecomputeRunResponse.from(run, LocalDateTime.now(), properties.staleAfter)

    private fun paging(page: Int, size: Int, sort: Sort = Sort.unsorted()): PageRequest {
        if (page < 0 || size !in 1..100) throw ServiceException("400-10", "page는 0 이상, size는 1~100이어야 합니다.")
        return PageRequest.of(page, size, sort)
    }

    companion object {
        val ACTIVE_STATUSES = listOf(
            RelatedGamePrecomputeRunStatus.ACCEPTED,
            RelatedGamePrecomputeRunStatus.PREPARING,
            RelatedGamePrecomputeRunStatus.RUNNING,
        )
    }
}
