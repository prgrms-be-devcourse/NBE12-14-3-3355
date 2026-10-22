package com.gamelog.nbe121423355.domain.review.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class ReviewCommentLikeId : Serializable {
    @Column(name = "user_id", nullable = false)
    final var userId: Long? = null
        private set

    @Column(name = "comment_id", nullable = false)
    final var commentId: Long? = null
        private set

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is ReviewCommentLikeId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$userId`: Any? = this.userId
        val `other$userId`: Any? = other.userId
        if (
            if (`this$userId` == null) `other$userId` != null
            else !`this$userId`.equals(`other$userId`)
        )
            return false
        val `this$commentId`: Any? = this.commentId
        val `other$commentId`: Any? = other.commentId
        if (
            if (`this$commentId` == null) `other$commentId` != null
            else !`this$commentId`.equals(`other$commentId`)
        )
            return false
        return true
    }

    protected fun canEqual(other: Any?): Boolean {
        return other is ReviewCommentLikeId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$userId`: Any? = this.userId
        result = result * PRIME + (if (`$userId` == null) 43 else `$userId`.hashCode())
        val `$commentId`: Any? = this.commentId
        result = result * PRIME + (if (`$commentId` == null) 43 else `$commentId`.hashCode())
        return result
    }

    protected constructor()

    constructor(userId: Long?, commentId: Long?) {
        this.userId = userId
        this.commentId = commentId
    }

    companion object {
        private const val serialVersionUId = 1L
    }
}
