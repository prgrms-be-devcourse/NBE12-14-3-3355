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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GamePlatformId || !other.canEqual(this)) return false
        return gameId == other.gameId && platformId == other.platformId
    }

    protected fun canEqual(other: Any?): Boolean = other is GamePlatformId

    override fun hashCode(): Int =
        59 * (59 + (gameId?.hashCode() ?: 43)) + (platformId?.hashCode() ?: 43)

    protected constructor()

    constructor(gameId: Long?, platformId: Long?) {
        this.gameId = gameId
        this.platformId = platformId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
