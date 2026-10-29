package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.GameSeries
import org.springframework.data.jpa.repository.JpaRepository

interface GameSeriesRepository : JpaRepository<GameSeries, Long> {
    fun findByIgdbId(igdbId: Long?): GameSeries?
}
