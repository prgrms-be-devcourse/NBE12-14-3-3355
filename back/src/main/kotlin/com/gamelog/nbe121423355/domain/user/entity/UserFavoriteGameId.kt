package com.gamelog.nbe121423355.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class UserFavoriteGameId : Serializable {
    @Column(name = "user_id", nullable = false)
    final var userId: Long? = null
        private set

    @Column(name = "game_id", nullable = false)
    final var gameId: Long? = null
        private set

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is UserFavoriteGameId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$userId`: Any? = this.userId
        val `other$userId`: Any? = other.userId
        if (
            if (`this$userId` == null) `other$userId` != null
            else !`this$userId`.equals(`other$userId`)
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
        return other is UserFavoriteGameId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$userId`: Any? = this.userId
        result = result * PRIME + (if (`$userId` == null) 43 else `$userId`.hashCode())
        val `$gameId`: Any? = this.gameId
        result = result * PRIME + (if (`$gameId` == null) 43 else `$gameId`.hashCode())
        return result
    }

    protected constructor()

    constructor(userId: Long?, gameId: Long?) {
        this.userId = userId
        this.gameId = gameId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
