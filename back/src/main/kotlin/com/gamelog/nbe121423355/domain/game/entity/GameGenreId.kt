package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class GameGenreId : Serializable {
    @Column(name = "game_id", nullable = false)
    final var gameId: Long? = null
        private set

    @Column(name = "genre_id", nullable = false)
    final var genreId: Long? = null
        private set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameGenreId || !other.canEqual(this)) return false
        return gameId == other.gameId && genreId == other.genreId
    }

    protected fun canEqual(other: Any?): Boolean = other is GameGenreId

    override fun hashCode(): Int =
        59 * (59 + (gameId?.hashCode() ?: 43)) + (genreId?.hashCode() ?: 43)

    protected constructor()

    constructor(gameId: Long?, genreId: Long?) {
        this.gameId = gameId
        this.genreId = genreId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
