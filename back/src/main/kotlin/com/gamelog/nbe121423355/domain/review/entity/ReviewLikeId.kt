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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReviewLikeId) return false
        return userId == other.userId && reviewId == other.reviewId
    }

    override fun hashCode(): Int {
        return 59 * (59 + (userId?.hashCode() ?: 43)) + (reviewId?.hashCode() ?: 43)
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
