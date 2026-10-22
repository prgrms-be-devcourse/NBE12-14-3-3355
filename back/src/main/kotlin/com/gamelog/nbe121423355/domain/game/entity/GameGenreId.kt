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

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is GameGenreId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$gameId`: Any? = this.gameId
        val `other$gameId`: Any? = other.gameId
        if (
            if (`this$gameId` == null) `other$gameId` != null
            else !`this$gameId`.equals(`other$gameId`)
        )
            return false
        val `this$genreId`: Any? = this.genreId
        val `other$genreId`: Any? = other.genreId
        if (
            if (`this$genreId` == null) `other$genreId` != null
            else !`this$genreId`.equals(`other$genreId`)
        )
            return false
        return true
    }

    protected fun canEqual(other: Any?): Boolean {
        return other is GameGenreId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$gameId`: Any? = this.gameId
        result = result * PRIME + (if (`$gameId` == null) 43 else `$gameId`.hashCode())
        val `$genreId`: Any? = this.genreId
        result = result * PRIME + (if (`$genreId` == null) 43 else `$genreId`.hashCode())
        return result
    }

    protected constructor()

    constructor(gameId: Long?, genreId: Long?) {
        this.gameId = gameId
        this.genreId = genreId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
