package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportCreateRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportStatusUpdateRequest
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewReportRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.math.BigDecimal
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class ReviewReportServiceTest {
    @Mock private lateinit var reviewReportRepository: ReviewReportRepository
    @Mock private lateinit var reviewRepository: ReviewRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var userGameService: UserGameService
    @Mock private lateinit var changeRecorder: RelatedGameChangeRecorder
    @Mock private lateinit var notificationService: NotificationService
    @InjectMocks private lateinit var reviewReportService: ReviewReportService

    private lateinit var writer: User
    private lateinit var reporter: User
    private lateinit var review: Review

    @BeforeEach
    fun setUp() {
        writer = mock(User::class.java)
        reporter = mock(User::class.java)
        val userGame = mock(UserGame::class.java)
        // 추천 변경 기록에 사용할 리뷰의 기준 게임을 준비한다.
        val game = mock(Game::class.java)
        lenient().`when`(game.id).thenReturn(6L)
        lenient().`when`(userGame.game).thenReturn(game)
        lenient().`when`(writer.id).thenReturn(1L)
        lenient().`when`(writer.nickname).thenReturn("리뷰작성자")
        lenient().`when`(reporter.id).thenReturn(2L)
        lenient().`when`(reporter.nickname).thenReturn("신고자")
        lenient().`when`(userGame.user).thenReturn(writer)
        review = Review(userGame, BigDecimal("4.5"), "신고 대상 리뷰", false)
        ReflectionTestUtils.setField(review, "id", 10L)
    }

    @DisplayName("다른 사용자의 활성 리뷰를 신고한다")
    @Test
    fun createReport() {
        `when`(reviewRepository.findActiveForUpdate(10L)).thenReturn(Optional.of(review))
        `when`(userRepository.findById(2L)).thenReturn(Optional.of(reporter))
        `when`(reviewReportRepository.existsByReview_IdAndReporter_Id(10L, 2L)).thenReturn(false)
        val reportMatcher = any(ReviewReport::class.java) ?: ReviewReport(review, reporter, "매처용 신고")
        `when`(reviewReportRepository.save(reportMatcher)).thenAnswer { it.getArgument<ReviewReport>(0) }

        val response = reviewReportService.createReport(2L, 10L, ReviewReportCreateRequest("욕설 포함"))

        assertThat(response.status).isEqualTo(ReportStatus.PENDING)
        assertThat(response.reason).isEqualTo("욕설 포함")
        assertThat(response.reviewContent).isEqualTo("신고 대상 리뷰")
        assertThat(response.reviewWriterNickname).isEqualTo("리뷰작성자")
        assertThat(response.reporterNickname).isEqualTo("신고자")
    }

    @DisplayName("같은 리뷰의 신고를 묶어 게임 ID·제목·이미지와 함께 조회한다")
    @Test
    fun getReportsGroupedByReview() {
        val game = mock(Game::class.java)
        `when`(review.userGame.game).thenReturn(game)
        `when`(game.id).thenReturn(100L)
        `when`(game.title).thenReturn("테스트 게임")
        `when`(game.coverImageUrl).thenReturn("https://example.com/cover.jpg")
        val anotherReporter = mock(User::class.java)
        `when`(anotherReporter.id).thenReturn(3L)
        `when`(anotherReporter.nickname).thenReturn("다른신고자")
        val firstReport = ReviewReport(review, reporter, "첫 번째 신고")
        val secondReport = ReviewReport(review, anotherReporter, "두 번째 신고")
        val pageable = PageRequest.of(0, 20)
        `when`(reviewReportRepository.findReviewIdsGroupedByReview(ReportStatus.PENDING, pageable))
            .thenReturn(PageImpl(listOf(10L), pageable, 1L))
        `when`(reviewReportRepository.findAllByReviewIdsAndStatus(listOf(10L), ReportStatus.PENDING))
            .thenReturn(listOf(firstReport, secondReport))

        val response = reviewReportService.getReports(ReportStatus.PENDING, pageable)

        val group = response.reports.first()
        assertThat(group.gameId).isEqualTo(100L)
        assertThat(group.gameTitle).isEqualTo("테스트 게임")
        assertThat(group.gameCoverImageUrl).isEqualTo("https://example.com/cover.jpg")
        assertThat(response.totalElements).isEqualTo(1L)
        assertThat(response.reports).hasSize(1)
        assertThat(group.reviewId).isEqualTo(10L)
        assertThat(group.reportCount).isEqualTo(2)
        assertThat(group.pendingReportCount).isEqualTo(2)
        assertThat(group.reports).hasSize(2)
    }

    @DisplayName("신고 조회 결과 순서와 관계없이 페이지의 리뷰 순서를 유지한다")
    @Test
    fun reportGroupsFollowPageOrder() {
        val game = mock(Game::class.java)
        `when`(review.userGame.game).thenReturn(game)
        val anotherReview = Review(review.userGame, BigDecimal.ONE, "다른 리뷰", false)
        ReflectionTestUtils.setField(anotherReview, "id", 11L)
        val firstReport = ReviewReport(review, reporter, "첫 신고")
        val secondReport = ReviewReport(anotherReview, reporter, "다른 게임 신고")
        val pageable = PageRequest.of(0, 20)
        `when`(reviewReportRepository.findReviewIdsGroupedByReview(ReportStatus.PENDING, pageable))
            .thenReturn(PageImpl(listOf(11L, 10L), pageable, 2L))
        `when`(reviewReportRepository.findAllByReviewIdsAndStatus(listOf(11L, 10L), ReportStatus.PENDING))
            .thenReturn(listOf(firstReport, secondReport))

        val response = reviewReportService.getReports(ReportStatus.PENDING, pageable)

        assertThat(response.reports.map { it.reviewId }).containsExactly(11L, 10L)
    }

    @DisplayName("신고를 승인하면 리뷰를 관리자 숨김 처리한다")
    @Test
    fun approveReportHidesReview() {
        val report = ReviewReport(review, reporter, "부적절한 내용")
        val anotherReporter = mock(User::class.java)
        lenient().`when`(anotherReporter.id).thenReturn(3L)
        lenient().`when`(anotherReporter.nickname).thenReturn("다른신고자")
        val anotherReport = ReviewReport(review, anotherReporter, "같은 리뷰의 다른 신고")
        `when`(reviewRepository.lockReviewIdByReportId(20L)).thenReturn(10L)
        `when`(reviewReportRepository.findPendingByReviewIdForUpdate(10L))
            .thenReturn(listOf(report, anotherReport))

        val response = reviewReportService.updateStatus(20L, ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))

        assertThat(response.status).isEqualTo(ReportStatus.APPROVED)
        assertThat(anotherReport.status).isEqualTo(ReportStatus.APPROVED)
        assertThat(review.status).isEqualTo(ReviewStatus.HIDDEN_BY_ADMIN)
        assertThat(review.deletedAt).isNotNull()
    }

    @DisplayName("신고를 반려하면 리뷰 공개 상태를 유지한다")
    @Test
    fun rejectReportKeepsReviewActive() {
        val report = ReviewReport(review, reporter, "문제 없음")
        `when`(reviewRepository.lockReviewIdByReportId(20L)).thenReturn(10L)
        `when`(reviewReportRepository.findPendingByReviewIdForUpdate(10L)).thenReturn(listOf(report))

        val response = reviewReportService.updateStatus(20L, ReviewReportStatusUpdateRequest(ReportStatus.REJECTED))

        assertThat(response.status).isEqualTo(ReportStatus.REJECTED)
        assertThat(review.status).isEqualTo(ReviewStatus.ACTIVE)
    }

    @DisplayName("이미 처리된 신고는 다시 처리할 수 없다")
    @Test
    fun rejectReprocessingReport() {
        `when`(reviewRepository.lockReviewIdByReportId(20L)).thenReturn(10L)
        `when`(reviewReportRepository.findPendingByReviewIdForUpdate(10L)).thenReturn(emptyList())

        assertThatThrownBy {
            reviewReportService.updateStatus(20L, ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))
        }.isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("409-4")
            }
    }
}
