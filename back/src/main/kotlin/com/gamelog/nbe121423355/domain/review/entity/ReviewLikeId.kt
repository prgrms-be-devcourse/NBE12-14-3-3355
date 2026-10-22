package com.gamelog.nbe121423355.domain.review.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class ReviewLikeId : Serializable {
    @Column(name = "user_id", nullable = false)
    final var userId: Long? = null
        private set

    @Column(name = "review_id", nullable = false)
    final var reviewId: Long? = null
        private set

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is ReviewLikeId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$userId`: Any? = this.userId
        val `other$userId`: Any? = other.userId
        if (
            if (`this$userId` == null) `other$userId` != null
            else !`this$userId`.equals(`other$userId`)
        )
            return false
        val `this$reviewId`: Any? = this.reviewId
        val `other$reviewId`: Any? = other.reviewId
        if (
            if (`this$reviewId` == null) `other$reviewId` != null
            else !`this$reviewId`.equals(`other$reviewId`)
        )
            return false
        return true
    }

    protected fun canEqual(other: Any?): Boolean {
        return other is ReviewLikeId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$userId`: Any? = this.userId
        result = result * PRIME + (if (`$userId` == null) 43 else `$userId`.hashCode())
        val `$reviewId`: Any? = this.reviewId
        result = result * PRIME + (if (`$reviewId` == null) 43 else `$reviewId`.hashCode())
        return result
    }

    constructor()

    constructor(userId: Long?, reviewId: Long?) {
        this.userId = userId
        this.reviewId = reviewId
    }

    companion object {
        private const val serialVersionUId = 1L
    }
}
