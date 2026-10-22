package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class GamePlatformId : Serializable {
    @Column(name = "game_id", nullable = false)
    final var gameId: Long? = null
        private set

    @Column(name = "platform_id", nullable = false)
    final var platformId: Long? = null
        private set

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is GamePlatformId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$gameId`: Any? = this.gameId
        val `other$gameId`: Any? = other.gameId
        if (
            if (`this$gameId` == null) `other$gameId` != null
            else !`this$gameId`.equals(`other$gameId`)
        )
            return false
        val `this$platformId`: Any? = this.platformId
        val `other$platformId`: Any? = other.platformId
        if (
            if (`this$platformId` == null) `other$platformId` != null
            else !`this$platformId`.equals(`other$platformId`)
        )
            return false
        return true
    }

    protected fun canEqual(other: Any?): Boolean {
        return other is GamePlatformId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$gameId`: Any? = this.gameId
        result = result * PRIME + (if (`$gameId` == null) 43 else `$gameId`.hashCode())
        val `$platformId`: Any? = this.platformId
        result = result * PRIME + (if (`$platformId` == null) 43 else `$platformId`.hashCode())
        return result
    }

    protected constructor()

    constructor(gameId: Long?, platformId: Long?) {
        this.gameId = gameId
        this.platformId = platformId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
