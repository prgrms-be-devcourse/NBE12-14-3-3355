package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameInputChange
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameInputChangeRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameRefreshTargetRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation.REPEATABLE_READ
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional

@Service
class RelatedGameChangePreparation(
    private val changes: RelatedGameInputChangeRepository,
    private val expansion: RelatedGameChangeExpansion,
) {
    // 호출자는 공통 계산 잠금을 유지한다. 페이지마다 읽기 시점을 바꾸지 않는다.
    @Transactional(readOnly = true, propagation = REQUIRES_NEW, isolation = REPEATABLE_READ)
    fun expandPending(checkOwnership: Runnable) {
        var afterId = 0L
        while (true) {
            checkOwnership.run()
            val page = changes.findPendingAfter(afterId, 100)
            if (page.isEmpty()) return
            for (change in page) {
                checkOwnership.run()
                expansion.expand(change)
                checkOwnership.run()
            }
            afterId = page.last().gameId
        }
    }
}

@Service
class RelatedGameChangeExpansion(
    private val changes: RelatedGameInputChangeRepository,
    private val targets: RelatedGameRefreshTargetRepository,
    private val jdbc: JdbcTemplate,
) {
    @Transactional(propagation = REQUIRES_NEW)
    fun expand(change: RelatedGameInputChange) {
        val current = changes.findByGameId(change.gameId) ?: error("변경 원천이 없습니다.")
        if (current.expandedVersion >= change.changeVersion) return
        val currentGenres = jdbc.queryForList(
            "SELECT genre_id FROM game_genres WHERE game_id = ?", Long::class.javaObjectType, change.gameId,
        ).map { requireNotNull(it) }
        targets.requestAffected(change.gameId, change.affectedGenreIds + currentGenres, change.reasonMask, change.changedAt)
        // 같은 트랜잭션으로 확인하되 캡처 이후에 들어온 새 원천은 보존한다.
        changes.markExpanded(change.gameId, change.changeVersion)
    }

    @Transactional(propagation = REQUIRES_NEW)
    fun initializeBaseline(upperId: Long) = targets.requestBaseline(upperId)
}
