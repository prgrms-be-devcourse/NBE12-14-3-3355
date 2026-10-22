package com.gamelog.nbe121423355.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class UserFollowId : Serializable {
    @Column(name = "follower_id", nullable = false)
    final var followerId: Long? = null
        private set

    @Column(name = "following_id", nullable = false)
    final var followingId: Long? = null
        private set

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is UserFollowId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$followerId`: Any? = this.followerId
        val `other$followerId`: Any? = other.followerId
        if (
            if (`this$followerId` == null) `other$followerId` != null
            else !`this$followerId`.equals(`other$followerId`)
        )
            return false
        val `this$followingId`: Any? = this.followingId
        val `other$followingId`: Any? = other.followingId
        if (
            if (`this$followingId` == null) `other$followingId` != null
            else !`this$followingId`.equals(`other$followingId`)
        )
            return false
        return true
    }

    protected fun canEqual(other: Any?): Boolean {
        return other is UserFollowId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$followerId`: Any? = this.followerId
        result = result * PRIME + (if (`$followerId` == null) 43 else `$followerId`.hashCode())
        val `$followingId`: Any? = this.followingId
        result = result * PRIME + (if (`$followingId` == null) 43 else `$followingId`.hashCode())
        return result
    }

    protected constructor()

    constructor(followerId: Long?, followingId: Long?) {
        this.followerId = followerId
        this.followingId = followingId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
