package com.gamelog.nbe121423355.domain.review.entity

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.interop.javaNonNull
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal

@jakarta.persistence.Entity
@jakarta.persistence.Table(
    name = "review_reports",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_review_report_review_reporter",
                columnNames = ["review_id", "reporter_id"],
            )
        ],
)
class ReviewReport : BaseEntity {
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    lateinit var review: Review
        protected set

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    lateinit var reporter: User
        protected set

    @jakarta.persistence.Column(nullable = false, length = 255)
    var reason: String? = null
        protected set

    // 신고 이후 작성자가 리뷰를 수정·삭제·재작성해도 신고 당시 원문을 보존합니다.
    @jakarta.persistence.Column(name = "review_rating_snapshot", precision = 2, scale = 1)
    var reviewRatingSnapshot: BigDecimal? = null
        protected set

    @jakarta.persistence.Column(name = "review_content_snapshot", columnDefinition = "TEXT")
    var reviewContentSnapshot: String? = null
        protected set

    @jakarta.persistence.Column(name = "review_spoiler_snapshot")
    var reviewSpoilerSnapshot: Boolean? = null
        protected set

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(length = 20)
    var status: ReportStatus? = null
        protected set

    constructor(review: Review, reporter: User?, reason: String?) {
        this.review = javaNonNull(review, "review는 필수입니다.")
        this.reporter = javaNonNull(reporter, "reporter는 필수입니다.")
        this.reason = ReviewReport.validateReason(reason)
        this.reviewRatingSnapshot = review.rating
        this.reviewContentSnapshot = review.content
        this.reviewSpoilerSnapshot = review.isSpoiler
        this.status = ReportStatus.PENDING
    }

    fun changeStatus(status: ReportStatus?) {
        this.status = javaNonNull(status, "status는 필수입니다.")
    }

    // 과거 신고는 스냅샷 전체가 null이다. 캡처된 별점·본문의 null은 당시 값으로 보존한다.
    fun hasReviewSnapshot(): Boolean =
        reviewRatingSnapshot != null || reviewContentSnapshot != null || reviewSpoilerSnapshot != null

    protected constructor()

    companion object {

        private fun validateReason(reason: String?): String {
            kotlin.require(!(reason == null || reason.javaIsBlank())) { "신고 사유는 필수입니다." }
            kotlin.require(!(reason.length > 255)) { "신고 사유는 255자를 초과할 수 없습니다." }
            return reason
        }
    }
}
