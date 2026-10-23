package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@JvmRecord
data class ReviewReportGroupResponse(
    val reviewId: Long?,
    val gameId: Long?,
    val gameTitle: String?,
    val gameCoverImageUrl: String?,
    val reviewWriterId: Long?,
    val reviewWriterNickname: String?,
    val reviewRating: BigDecimal?,
    val reviewContent: String?,
    val reviewSpoiler: Boolean,
    val reviewStatus: ReviewStatus?,
    val status: ReportStatus?,
    val reportCount: Int,
    val pendingReportCount: Int,
    val representativeReportId: Long?,
    val latestReportedAt: LocalDateTime?,
    val reports: List<ReviewReportItemResponse>?,
) {

    companion object {
        @JvmStatic
        fun from(source: List<ReviewReport>): ReviewReportGroupResponse {
            require(source.isNotEmpty()) { "신고 묶음은 비어 있을 수 없습니다." }

            val sorted = source.sortedWith(compareBy(nullsLast(reverseOrder())) { it.createdDate })

            val representative =
                sorted.firstOrNull { it.status == ReportStatus.PENDING } ?: sorted.first()

            val groupStatus =
                if (sorted.any { it.status == ReportStatus.PENDING })
                    ReportStatus.PENDING
                else if (sorted.any { it.status == ReportStatus.APPROVED })
                    ReportStatus.APPROVED
                else ReportStatus.REJECTED

            return ReviewReportGroupResponse(
                representative.review.id,
                representative.review.userGame.game.id,
                representative.review.userGame.game.title,
                representative.review.userGame.game.coverImageUrl,
                representative.review.userGame.user.id,
                representative.review.userGame.user.nickname,
                if (representative.reviewRatingSnapshot != null) representative.reviewRatingSnapshot
                else representative.review.rating,
                if (representative.reviewContentSnapshot != null)
                    representative.reviewContentSnapshot
                else representative.review.content,
                representative.reviewSpoilerSnapshot ?: representative.review.isSpoiler,
                if (representative.review.status == null) ReviewStatus.ACTIVE
                else representative.review.status,
                groupStatus,
                sorted.size,
                sorted.count { it.status == ReportStatus.PENDING },
                representative.id,
                representative.createdDate,
                sorted.map { ReviewReportItemResponse.from(it) },
            )
        }
    }
}
