package com.gamelog.nbe121423355.domain.review.entity

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.interop.javaNonNull
import com.gamelog.nbe121423355.global.interop.strip
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*

@jakarta.persistence.Entity
@jakarta.persistence.Table(
    name = "review_comment",
    indexes =
        [
            jakarta.persistence.Index(
                name = "idx_comment_review_parent_id",
                columnList = "review_id,parent_id,id",
            )
        ],
)
class ReviewComment : BaseEntity {
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    lateinit var review: Review
        protected set

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    @jakarta.persistence.Column(nullable = false, columnDefinition = "TEXT")
    var content: String?? = null
        protected set

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: ReviewComment? = null
        protected set

    @jakarta.persistence.Column(nullable = false)
    var spoiler: Boolean = false
        protected set

    @jakarta.persistence.Column(nullable = false)
    var deleted: Boolean = false
        protected set

    @jakarta.persistence.Column(nullable = false)
    var edited: Boolean = false
        protected set

    constructor(
        review: Review,
        user: User?,
        content: String?,
        spoiler: Boolean,
        parent: ReviewComment?,
    ) : this(review, user, content) {
        kotlin.require(
            !(parent != null &&
                (parent.parent != null || parent.deleted || parent.review.id != review.id))
        ) {
            "답글 대상이 올바르지 않습니다."
        }
        this.parent = parent
        this.spoiler = spoiler
    }

    constructor(review: Review?, user: User?, content: String?) {
        this.review = javaNonNull(review)
        this.user = javaNonNull(user)
        this.content = ReviewComment.validate(content)
    }

    fun editContent(content: String?) {
        this.content = ReviewComment.validate(content)
        this.edited = true
    }

    fun edit(content: String?, spoiler: Boolean) {
        editContent(content)
        this.spoiler = spoiler
    }

    fun delete() {
        this.content = ""
        this.spoiler = false
        this.deleted = true
    }

    constructor()

    companion object {

        private fun validate(content: String?): String {
            kotlin.require(!(content == null || content.javaIsBlank() || content.length > 1000)) {
                "댓글은 공백이 아닌 1~1000자여야 합니다."
            }
            return content.strip()
        }
    }

    val isSpoiler: Boolean
        get() = spoiler

    val isDeleted: Boolean
        get() = deleted

    val isEdited: Boolean
        get() = edited
}
