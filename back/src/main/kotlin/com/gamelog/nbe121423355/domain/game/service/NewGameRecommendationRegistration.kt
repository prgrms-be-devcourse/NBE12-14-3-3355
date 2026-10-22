package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Service
class NewGameRecommendationRegistration
constructor(
    private val calculations: RelatedGameCalculationRepository,
    private val events: ApplicationEventPublisher,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun register(newGames: MutableList<Game>) {
        if (newGames.isEmpty()) return
        calculations.saveAll(
            newGames.stream().map({ game -> RelatedGameCalculation(game) }).toList()
        )
        events.publishEvent(GamesAdded(newGames.map { requireNotNull(it.id) }.toMutableList()))
    }

    class GamesAdded(gameIds: MutableList<Long>) {
        val gameIds: MutableList<Long> = java.util.List.copyOf(gameIds)

        fun gameIds() = gameIds

        override fun equals(other: Any?): Boolean = other is GamesAdded && gameIds == other.gameIds

        override fun hashCode(): Int = gameIds.hashCode()
    }
}
