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

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is GameSeriesGameId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$seriesId`: Any? = this.seriesId
        val `other$seriesId`: Any? = other.seriesId
        if (
            if (`this$seriesId` == null) `other$seriesId` != null
            else !`this$seriesId`.equals(`other$seriesId`)
        )
            return false
        val `this$gameId`: Any? = this.gameId
        val `other$gameId`: Any? = other.gameId
        if (
            if (`this$gameId` == null) `other$gameId` != null
            else !`this$gameId`.equals(`other$gameId`)
        )
            return false
        return true
    }

    protected fun canEqual(other: Any?): Boolean {
        return other is GameSeriesGameId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$seriesId`: Any? = this.seriesId
        result = result * PRIME + (if (`$seriesId` == null) 43 else `$seriesId`.hashCode())
        val `$gameId`: Any? = this.gameId
        result = result * PRIME + (if (`$gameId` == null) 43 else `$gameId`.hashCode())
        return result
    }

    protected constructor()

    constructor(seriesId: Long?, gameId: Long?) {
        this.seriesId = seriesId
        this.gameId = gameId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
