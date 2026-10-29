package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class GameSeriesGameId : Serializable {
    @Column(name = "series_id", nullable = false)
    final var seriesId: Long? = null
        private set

    @Column(name = "game_id", nullable = false)
    final var gameId: Long? = null
        private set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameSeriesGameId || !other.canEqual(this)) return false
        return seriesId == other.seriesId && gameId == other.gameId
    }

    protected fun canEqual(other: Any?): Boolean = other is GameSeriesGameId

    override fun hashCode(): Int =
        59 * (59 + (seriesId?.hashCode() ?: 43)) + (gameId?.hashCode() ?: 43)

    protected constructor()

    constructor(seriesId: Long?, gameId: Long?) {
        this.seriesId = seriesId
        this.gameId = gameId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
