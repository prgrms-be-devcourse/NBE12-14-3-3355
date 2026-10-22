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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReviewCommentLikeId) return false
        return userId == other.userId && commentId == other.commentId
    }

    override fun hashCode(): Int {
        return 59 * (59 + (userId?.hashCode() ?: 43)) + (commentId?.hashCode() ?: 43)
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
