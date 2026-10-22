package com.gamelog.nbe121423355.domain.review.entity

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.global.interop.javaNonNull
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.FetchType.LAZY
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table

@Entity
@Table(name = "review_likes")
class ReviewLike {
    @EmbeddedId var id: ReviewLikeId = ReviewLikeId()

    @MapsId("reviewId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    lateinit var review: Review
        protected set

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    constructor(review: Review?, user: User?) {
        this.review = javaNonNull(review, "review는 필수입니다.")
        this.user = javaNonNull(user, "user는 필수입니다.")
    }

    protected constructor()
}
