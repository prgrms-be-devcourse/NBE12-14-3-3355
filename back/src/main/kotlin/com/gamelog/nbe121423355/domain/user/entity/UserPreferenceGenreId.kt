package com.gamelog.nbe121423355.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class UserPreferenceGenreId : Serializable {
    @Column(name = "user_id", nullable = false)
    final var userId: Long? = null
        private set

    @Column(name = "genre_id", nullable = false)
    final var genreId: Long? = null
        private set

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is UserPreferenceGenreId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$userId`: Any? = this.userId
        val `other$userId`: Any? = other.userId
        if (
            if (`this$userId` == null) `other$userId` != null
            else !`this$userId`.equals(`other$userId`)
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
        return other is UserPreferenceGenreId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$userId`: Any? = this.userId
        result = result * PRIME + (if (`$userId` == null) 43 else `$userId`.hashCode())
        val `$genreId`: Any? = this.genreId
        result = result * PRIME + (if (`$genreId` == null) 43 else `$genreId`.hashCode())
        return result
    }

    protected constructor()

    constructor(userId: Long?, genreId: Long?) {
        this.userId = userId
        this.genreId = genreId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
