package com.gamelog.nbe121423355.domain.review.entity

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.global.interop.javaNonNull
import jakarta.persistence.*

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "review_comment_likes")
class ReviewCommentLike {
    @EmbeddedId
    var id: ReviewCommentLikeId? = null
        protected set

    @MapsId("commentId")
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    lateinit var reviewComment: ReviewComment
        protected set

    @MapsId("userId")
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    constructor(reviewComment: ReviewComment, user: User) {
        this.reviewComment = javaNonNull(reviewComment)
        this.user = javaNonNull(user)
        this.id = ReviewCommentLikeId(javaNonNull(user.id), javaNonNull(reviewComment.id))
    }

    protected constructor()
}
