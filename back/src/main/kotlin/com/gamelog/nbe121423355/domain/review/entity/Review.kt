package com.gamelog.nbe121423355.domain.review.entity

import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.interop.javaNonNull
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.time.LocalDateTime

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "reviews")
class Review : BaseEntity {
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @jakarta.persistence.Version
    @jakarta.persistence.Column(nullable = false)
    var version: Long? = null
        protected set

    @jakarta.persistence.OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_game_id", nullable = false, unique = true)
    lateinit var userGame: UserGame
        protected set

    @jakarta.persistence.Column(precision = 2, scale = 1)
    var rating: BigDecimal? = null
        protected set

    @jakarta.persistence.Column(columnDefinition = "TEXT")
    var content: String? = null
        protected set

    @jakarta.persistence.Column(name = "is_spoiler", nullable = false)
    var spoiler: Boolean = false
        protected set

    // 기존 데이터는 status가 null일 수 있으므로 null도 ACTIVE로 취급합니다.
    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(length = 30)
    var status: ReviewStatus? = ReviewStatus.ACTIVE
        protected set

    @jakarta.persistence.Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    constructor(userGame: UserGame?, rating: BigDecimal?, content: String?, spoiler: Boolean) {
        this.userGame = javaNonNull(userGame, "userGame은 필수입니다.")
        this.rating = Review.validateRating(rating)
        this.content = Review.validateContent(rating, content)
        this.spoiler = spoiler
    }

    fun edit(rating: BigDecimal?, content: String?, spoiler: Boolean) {
        this.rating = Review.validateRating(rating)
        this.content = Review.validateContent(rating, content)
        this.spoiler = spoiler
    }

    fun restore(rating: BigDecimal?, content: String?, spoiler: Boolean) {
        edit(rating, content, spoiler)
        this.status = ReviewStatus.ACTIVE
        this.deletedAt = null
    }

    fun deleteByUser() {
        this.status = ReviewStatus.DELETED_BY_USER
        this.deletedAt = LocalDateTime.now()
    }

    fun hideByAdmin() {
        this.status = ReviewStatus.HIDDEN_BY_ADMIN
        this.deletedAt = LocalDateTime.now()
    }

    val isActive: Boolean
        get() = status == null || status == ReviewStatus.ACTIVE

    fun hasVisibleContent(): Boolean {
        return this.isActive && (rating != null || (content?.javaIsBlank() == false))
    }

    val isDeletedByUser: Boolean
        get() = status == ReviewStatus.DELETED_BY_USER

    protected constructor()

    companion object {
        private val MIN_RATING: BigDecimal = BigDecimal("0.5")
        private val MAX_RATING: BigDecimal = BigDecimal("5.0")
        private val RATING_STEP: BigDecimal = BigDecimal("0.5")

        private fun validateRating(rating: BigDecimal?): BigDecimal? {
            if (rating == null) {
                return null
            }
            val outOfRange =
                rating.compareTo(Review.MIN_RATING) < 0 || rating.compareTo(Review.MAX_RATING) > 0
            val invalidStep = rating.remainder(Review.RATING_STEP).compareTo(BigDecimal.ZERO) != 0
            kotlin.require(!(outOfRange || invalidStep)) { "rating은 0.5부터 5.0까지 0.5 단위여야 합니다." }
            return rating
        }

        private fun validateContent(rating: BigDecimal?, content: String?): String? {
            kotlin.require(!(rating == null && (content == null || content.javaIsBlank()))) {
                "별점 또는 리뷰 내용 중 하나는 입력해야 합니다."
            }
            return content
        }
    }

    val isSpoiler: Boolean
        get() = spoiler
}
