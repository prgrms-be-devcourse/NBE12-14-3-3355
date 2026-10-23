package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.util.ReflectionTestUtils

class ReviewReportGroupResponseTest {
    private lateinit var review: Review
    private lateinit var reporter: User

    @BeforeEach
    fun setUp() {
        val userGame = mock(UserGame::class.java)
        reporter = mock(User::class.java)
        `when`(userGame.user).thenReturn(reporter)
        `when`(userGame.game).thenReturn(mock(Game::class.java))
        review = Review(userGame, BigDecimal.ONE, "신고 대상 리뷰", false)
    }

    @Test
    @DisplayName("최신 신고부터 정렬하며 날짜가 없는 신고는 마지막에 유지한다")
    fun ordersNewestFirstWithNullDatesLast() {
        val older = report(1L, ReportStatus.REJECTED, LocalDateTime.of(2026, 10, 20, 10, 0))
        val newer = report(2L, ReportStatus.REJECTED, LocalDateTime.of(2026, 10, 21, 10, 0))
        val undated = report(3L, ReportStatus.REJECTED, null)

        val result = ReviewReportGroupResponse.from(listOf(undated, older, newer))

        assertThat(checkNotNull(result.reports).map { it.reportId }).containsExactly(2L, 1L, 3L)
        assertThat(result.representativeReportId).isEqualTo(2L)
    }

    @Test
    @DisplayName("최신 미처리 신고를 대표로 선택하고 미처리 상태를 우선한다")
    fun selectsNewestPendingReport() {
        val oldPending = report(1L, ReportStatus.PENDING, LocalDateTime.of(2026, 10, 20, 10, 0))
        val newPending = report(2L, ReportStatus.PENDING, LocalDateTime.of(2026, 10, 21, 10, 0))
        val approved = report(3L, ReportStatus.APPROVED, LocalDateTime.of(2026, 10, 22, 10, 0))

        val result = ReviewReportGroupResponse.from(listOf(oldPending, approved, newPending))

        assertThat(result.representativeReportId).isEqualTo(2L)
        assertThat(result.status).isEqualTo(ReportStatus.PENDING)
        assertThat(result.pendingReportCount).isEqualTo(2)
        assertThat(result.reportCount).isEqualTo(3)
    }

    @Test
    @DisplayName("미처리 신고가 없으면 승인 상태를 반려보다 우선한다")
    fun approvedTakesPriorityOverRejected() {
        val approved = report(1L, ReportStatus.APPROVED, LocalDateTime.of(2026, 10, 20, 10, 0))
        val rejected = report(2L, ReportStatus.REJECTED, LocalDateTime.of(2026, 10, 21, 10, 0))

        val result = ReviewReportGroupResponse.from(listOf(approved, rejected))

        assertThat(result.status).isEqualTo(ReportStatus.APPROVED)
        assertThat(result.representativeReportId).isEqualTo(2L)
        assertThat(result.pendingReportCount).isZero()
    }

    private fun report(id: Long, status: ReportStatus, createdDate: LocalDateTime?): ReviewReport {
        return ReviewReport(review, reporter, "신고 사유").also {
            ReflectionTestUtils.setField(it, "id", id)
            ReflectionTestUtils.setField(it, "createdDate", createdDate)
            it.changeStatus(status)
        }
    }
}
