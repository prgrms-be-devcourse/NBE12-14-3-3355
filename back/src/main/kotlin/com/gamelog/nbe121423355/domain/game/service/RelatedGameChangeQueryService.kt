package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputePage
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameInputChangeRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameRefreshTargetRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@JvmRecord
data class RelatedGameChangeSummary(val unexpandedGames: Long, val pendingTargets: Long)

@JvmRecord
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
) {
    fun summary() = RelatedGameChangeSummary(changes.countPending(), targets.countPending())

    fun pending(page: Int, size: Int): AdminRelatedGamePrecomputePage<RelatedGamePendingTarget> {
        if (page < 0 || size !in 1..100) throw ServiceException("400-10", "page는 0 이상, size는 1~100이어야 합니다.")
        val total = targets.countPending()
        return AdminRelatedGamePrecomputePage(
            targets.findPendingPage(page.toLong() * size, size).map { target ->
                RelatedGamePendingTarget(target.gameId, target.requiredVersion, target.appliedVersion,
                    RelatedGameChangeReason.entries.filter { it.mask and target.reasonMask != 0L }.map { it.name },
                    target.changedAt)
            }, page, size, total, ((total + size - 1) / size).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }
}
