package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Service
class NewGameRecommendationRegistration(
    private val calculations: RelatedGameCalculationRepository,
    private val events: ApplicationEventPublisher,
) {
    @Transactional(propagation = MANDATORY)
    fun register(newGames: List<Game>) {
        if (newGames.isEmpty()) return
        // 게임 등록 트랜잭션 안에서 계산 대기 상태를 함께 저장한다.
        calculations.saveAll(newGames.map { RelatedGameCalculation(it) })
        events.publishEvent(GamesAdded(newGames.map { requireNotNull(it.id) { "저장된 게임이 필요합니다." } }))
    }

    class GamesAdded(gameIds: List<Long>) {
        // 발행 후 원본 목록이 바뀌어도 커밋 후 계산할 게임 범위는 유지한다.
        val gameIds: List<Long> = java.util.List.copyOf(gameIds)

        override fun equals(other: Any?): Boolean = other is GamesAdded && gameIds == other.gameIds

        override fun hashCode(): Int = gameIds.hashCode()
    }
}
