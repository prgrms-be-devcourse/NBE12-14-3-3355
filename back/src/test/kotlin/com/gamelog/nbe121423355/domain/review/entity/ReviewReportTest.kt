package com.gamelog.nbe121423355.domain.review.entity

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class ReviewReportTest {
    @DisplayName("신고 생성 시점의 리뷰 내용을 스냅샷으로 보존한다")
    @Test
    fun preserveReportedReviewSnapshot() {
        val review = Review(mock(UserGame::class.java), BigDecimal("4.5"), "신고 당시 원문", true)
        val report = ReviewReport(review, mock(User::class.java), "부적절한 내용")

        review.edit(BigDecimal("1.0"), "신고 후 수정한 글", false)
        review.deleteByUser()

        assertThat(report.reviewRatingSnapshot).isEqualByComparingTo("4.5")
        assertThat(report.reviewContentSnapshot).isEqualTo("신고 당시 원문")
        assertThat(report.reviewSpoilerSnapshot).isTrue()
        assertThat(report.status).isEqualTo(ReportStatus.PENDING)
    }
}
