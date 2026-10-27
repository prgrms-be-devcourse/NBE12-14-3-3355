package com.gamelog.nbe121423355.domain.review.dto.response

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Platform
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.util.ReflectionTestUtils

class ReviewResponseNullFallbackTest {
    private lateinit var record: UserGame
    private lateinit var review: Review
    private lateinit var reporter: User

    @BeforeEach
    fun setUp() {
        record = mock(UserGame::class.java)
        reporter = mock(User::class.java)
        `when`(record.user).thenReturn(reporter)
        `when`(record.game).thenReturn(mock(Game::class.java))
        review = Review(record, BigDecimal("1.0"), "신고 당시 내용", false)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @DisplayName("신고 응답은 스냅샷이 있으면 보존하고 null일 때만 현재 리뷰 값으로 대체한다")
    fun preservesSnapshotAndFallsBackOnlyForNull(hasSnapshot: Boolean) {
        val report = ReviewReport(review, reporter, "신고 사유")
        if (!hasSnapshot) {
            for (field in listOf("reviewRatingSnapshot", "reviewContentSnapshot", "reviewSpoilerSnapshot")) {
                ReflectionTestUtils.setField(report, field, null)
            }
        }
        review.edit(BigDecimal("4.5"), "수정된 내용", true)
        val single = ReviewReportResponse.from(report)
        val group = ReviewReportGroupResponse.from(listOf(report))
        val expectedRating = if (hasSnapshot) "1.0" else "4.5"
        val expectedContent = if (hasSnapshot) "신고 당시 내용" else "수정된 내용"
        assertThat(single.reviewRating).isEqualByComparingTo(expectedRating)
        assertThat(group.reviewRating).isEqualByComparingTo(expectedRating)
        assertThat(single.reviewContent).isEqualTo(expectedContent)
        assertThat(group.reviewContent).isEqualTo(expectedContent)
        assertThat(single.reviewSpoiler).isEqualTo(!hasSnapshot)
        assertThat(group.reviewSpoiler).isEqualTo(!hasSnapshot)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @DisplayName("리뷰 상태는 null일 때만 ACTIVE로 표시하고 명시적인 숨김 상태는 유지한다")
    fun defaultsOnlyNullStatus(missingStatus: Boolean) {
        ReflectionTestUtils.setField(review, "status", if (missingStatus) null else ReviewStatus.HIDDEN_BY_ADMIN)
        val report = ReviewReport(review, reporter, "신고 사유")
        val expected = if (missingStatus) ReviewStatus.ACTIVE else ReviewStatus.HIDDEN_BY_ADMIN
        assertThat(ReviewReportResponse.from(report).reviewStatus).isEqualTo(expected)
        assertThat(ReviewReportGroupResponse.from(listOf(report)).reviewStatus).isEqualTo(expected)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @DisplayName("선택 플랫폼이 있으면 이름을 반환하고 없으면 null을 반환한다")
    fun mapsOptionalPlatform(hasPlatform: Boolean) {
        if (hasPlatform) {
            val platform = mock(Platform::class.java)
            `when`(platform.name).thenReturn("PC")
            `when`(record.platform).thenReturn(platform)
        }
        assertThat(ReviewResponse.from(review).platformName).isEqualTo(if (hasPlatform) "PC" else null)
    }
}
