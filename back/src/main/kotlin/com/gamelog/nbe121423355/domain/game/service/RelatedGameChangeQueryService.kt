package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputePage
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameInputChangeRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameRefreshTargetRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameChangePreviewRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePreviewTarget
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation.REPEATABLE_READ

data class RelatedGameChangeSummary(val unexpandedGames: Long, val pendingTargets: Long)

data class RelatedGamePendingTarget(
    val gameId: Long,
    val requiredVersion: Long,
    val appliedVersion: Long,
    val reasons: List<String>,
    val changedAt: LocalDateTime,
)

@Service
@Transactional(readOnly = true)
class RelatedGameChangeQueryService(
    private val changes: RelatedGameInputChangeRepository,
    private val targets: RelatedGameRefreshTargetRepository,
    private val previewTargets: RelatedGameChangePreviewRepository,
) {
    fun summary() = RelatedGameChangeSummary(
        unexpandedGames = changes.countPending(),
        pendingTargets = targets.countPending(),
    )

    @Transactional(readOnly = true, isolation = REPEATABLE_READ)
    fun preview(page: Int, size: Int): AdminRelatedGamePrecomputePage<RelatedGamePreviewTarget> {
        validatePaging(page, size)
        val total = previewTargets.count()
        return AdminRelatedGamePrecomputePage(
            content = previewTargets.findPage(page.toLong() * size, size),
            page = page,
            size = size,
            totalElements = total,
            totalPages = totalPages(total, size),
        )
    }

    fun pending(page: Int, size: Int): AdminRelatedGamePrecomputePage<RelatedGamePendingTarget> {
        validatePaging(page, size)
        val total = targets.countPending()
        return AdminRelatedGamePrecomputePage(
            content = targets.findPendingPage(page.toLong() * size, size).map { target ->
                RelatedGamePendingTarget(
                    gameId = target.gameId,
                    requiredVersion = target.requiredVersion,
                    appliedVersion = target.appliedVersion,
                    reasons = RelatedGameChangeReason.entries
                        .filter { it.mask and target.reasonMask != 0L }
                        .map { it.name },
                    changedAt = target.changedAt,
                )
            },
            page = page,
            size = size,
            totalElements = total,
            totalPages = totalPages(total, size),
        )
    }

    private fun validatePaging(page: Int, size: Int) {
        if (page < 0 || size !in 1..100) {
            throw ServiceException("400-10", "page는 0 이상, size는 1~100이어야 합니다.")
        }
    }

    private fun totalPages(total: Long, size: Int): Int =
        ((total + size - 1) / size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
