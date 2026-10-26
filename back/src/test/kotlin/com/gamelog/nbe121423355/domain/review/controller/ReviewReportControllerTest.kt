package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportCreateRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportStatusUpdateRequest
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewReportRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/** 실제 JWT의 USER/ADMIN 권한과 신고·관리자 처리 API의 저장 결과를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class ReviewReportControllerTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var reports: ReviewReportRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var records: UserGameRepository

    private lateinit var writer: User
    private lateinit var reporter: User
    private lateinit var anotherReporter: User
    private lateinit var admin: User
    private var reviewId = 0L
    private var reportId = 0L
    private var recordId = 0L
    private val listUrl = "/api/v1/admin/review-reports"
    private val createUrl get() = "/api/v1/reviews/$reviewId/reports"
    private val statusUrl get() = "$listUrl/$reportId/status"

    @BeforeEach
    fun setUp() {
        writer = User("report-api-writer", "report-api-writer@test.com", "password")
        reporter = User("report-api-reporter", "report-api-reporter@test.com", "password")
        anotherReporter = User("report-api-second", "report-api-second@test.com", "password")
        admin = User("report-api-admin", "report-api-admin@test.com", "password").apply { promoteToAdmin() }
        val game = Game.createFromIgdb(IgdbGameResponse(
            987601L, "신고 API 게임", "test", null, null, null,
            mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
        ))
        listOf(writer, reporter, anotherReporter, admin, game).forEach(em::persist)
        val record = UserGame(writer, game)
        em.persist(record)
        val review = Review(record, BigDecimal("4.0"), "신고 당시 리뷰", true)
        em.persist(review)
        record.updateLibraryStatus(hasActiveReview = true)
        val report = ReviewReport(review, reporter, "기존 신고 사유")
        em.persist(report)
        em.flush()
        reviewId = checkNotNull(review.id)
        reportId = checkNotNull(report.id)
        recordId = checkNotNull(record.id)
        em.clear()
    }

    @Test
    @DisplayName("신고 접수는 201과 대기 상태·작성자·신고 당시 스냅샷을 반환한다")
    fun createsReportAndCapturesSnapshot() {
        mvc.perform(post(createUrl).authorized(anotherReporter).json(ReviewReportCreateRequest("욕설 신고")))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resultCode").value("201-1"))
            .andExpect(jsonPath("$.data.reportId").isNumber)
            .andExpect(jsonPath("$.data.reviewId").value(reviewId))
            .andExpect(jsonPath("$.data.reporterId").value(anotherReporter.id))
            .andExpect(jsonPath("$.data.reviewWriterId").value(writer.id))
            .andExpect(jsonPath("$.data.reason").value("욕설 신고"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.reviewRating").value(4.0))
            .andExpect(jsonPath("$.data.reviewContent").value("신고 당시 리뷰"))
            .andExpect(jsonPath("$.data.reviewSpoiler").value(true))
        flushAndClear()
        val saved = reports.findAll().single { it.reporter.id == anotherReporter.id }
        assertThat(saved.reason).isEqualTo("욕설 신고")
        assertThat(saved.reviewContentSnapshot).isEqualTo("신고 당시 리뷰")
        assertThat(saved.status).isEqualTo(ReportStatus.PENDING)
    }

    @Test
    @DisplayName("신고 후 리뷰가 수정되어도 관리자 조회에는 신고 당시 내용이 남는다")
    fun preservesSnapshotAfterReviewEdit() {
        reviews.findById(reviewId).orElseThrow().edit(BigDecimal("2.0"), "수정된 내용", false)
        flushAndClear()
        mvc.perform(get(listUrl).authorized(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.reports[0].reviewContent").value("신고 당시 리뷰"))
            .andExpect(jsonPath("$.data.reports[0].reviewRating").value(4.0))
            .andExpect(jsonPath("$.data.reports[0].reviewSpoiler").value(true))
    }

    @Test
    @DisplayName("관리자 목록은 같은 리뷰의 신고를 하나로 묶고 리뷰 기준 페이징을 반환한다")
    fun listsGroupedReportsWithPagination() {
        addSecondPendingReport()
        mvc.perform(get(listUrl).authorized(admin).param("status", "PENDING").param("page", "0").param("size", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-7"))
            .andExpect(jsonPath("$.data.reports.length()").value(1))
            .andExpect(jsonPath("$.data.reports[0].reviewId").value(reviewId))
            .andExpect(jsonPath("$.data.reports[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data.reports[0].reportCount").value(2))
            .andExpect(jsonPath("$.data.reports[0].pendingReportCount").value(2))
            .andExpect(jsonPath("$.data.reports[0].reports.length()").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    @DisplayName("조회 상태에 해당하는 신고가 없으면 빈 페이지를 반환한다")
    fun filtersReportsByStatus() {
        mvc.perform(get(listUrl).authorized(admin).param("status", "APPROVED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.reports").isEmpty)
            .andExpect(jsonPath("$.data.totalElements").value(0))
    }

    @ParameterizedTest
    @EnumSource(value = ReportStatus::class, names = ["APPROVED", "REJECTED"])
    @DisplayName("관리자 처리 시 같은 리뷰의 대기 신고를 모두 처리하고 승인일 때만 리뷰를 숨긴다")
    fun handlesAllPendingReports(target: ReportStatus) {
        addSecondPendingReport()
        assertThat(records.findById(recordId).orElseThrow().isInLibrary).isTrue()
        mvc.perform(put(statusUrl).authorized(admin).json(ReviewReportStatusUpdateRequest(target)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-8"))
            .andExpect(jsonPath("$.data.reviewId").value(reviewId))
            .andExpect(jsonPath("$.data.status").value(target.name))
            .andExpect(jsonPath("$.data.reviewStatus").value(
                if (target == ReportStatus.APPROVED) "HIDDEN_BY_ADMIN" else "ACTIVE"))
        flushAndClear()
        val saved = reports.findAll()
        assertThat(saved).hasSize(2)
        assertThat(saved.map { it.status }).containsOnly(target)
        val expected = if (target == ReportStatus.APPROVED) ReviewStatus.HIDDEN_BY_ADMIN else ReviewStatus.ACTIVE
        assertThat(reviews.findById(reviewId).orElseThrow().status).isEqualTo(expected)
        assertThat(records.findById(recordId).orElseThrow().isInLibrary)
            .isEqualTo(target != ReportStatus.APPROVED)
    }

    @ParameterizedTest
    @ValueSource(strings = ["create", "list", "update"])
    @DisplayName("인증 없는 신고 접수·목록·처리 요청은 401이다")
    fun rejectsUnauthenticatedRequests(action: String) {
        mvc.perform(requestFor(action))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-1"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["list", "update"])
    @DisplayName("일반 사용자는 관리자 신고 목록·처리에 접근할 수 없다")
    fun rejectsNonAdmin(action: String) {
        mvc.perform(requestFor(action).authorized(reporter))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))
        assertThat(reports.findById(reportId).orElseThrow().status).isEqualTo(ReportStatus.PENDING)
        assertThat(reviews.findById(reviewId).orElseThrow().status).isEqualTo(ReviewStatus.ACTIVE)
    }

    @Test
    @DisplayName("본인의 리뷰 신고는 400이다")
    fun rejectsSelfReport() {
        mvc.perform(post(createUrl).authorized(writer).json(ReviewReportCreateRequest("본인 신고")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-3"))
    }

    @Test
    @DisplayName("같은 사용자의 중복 신고는 409이다")
    fun rejectsDuplicateReport() {
        mvc.perform(post(createUrl).authorized(reporter).json(ReviewReportCreateRequest("중복 신고")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-1"))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   "])
    @DisplayName("신고 사유가 없거나 공백이면 400이다")
    fun requiresNonBlankReason(reason: String?) {
        mvc.perform(post(createUrl).authorized(anotherReporter).json(ReviewReportCreateRequest(reason)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
        assertThat(reports.count()).isEqualTo(1)
    }

    @Test
    @DisplayName("255자를 초과하는 신고 사유는 400이다")
    fun rejectsLongReason() {
        mvc.perform(post(createUrl).authorized(anotherReporter).json(ReviewReportCreateRequest("가".repeat(256))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }

    @Test
    @DisplayName("존재하지 않는 리뷰 신고는 404이다")
    fun rejectsMissingReview() {
        mvc.perform(post("/api/v1/reviews/${Long.MAX_VALUE}/reports").authorized(anotherReporter)
            .json(ReviewReportCreateRequest("신고")))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-3"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["deleted", "hidden"])
    @DisplayName("삭제·숨김 리뷰의 신규 신고는 404이다")
    fun rejectsInvisibleReview(target: String) {
        val review = reviews.findById(reviewId).orElseThrow()
        if (target == "deleted") review.deleteByUser() else review.hideByAdmin()
        flushAndClear()
        mvc.perform(post(createUrl).authorized(anotherReporter).json(ReviewReportCreateRequest("신고")))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-3"))
    }

    @Test
    @DisplayName("신고 처리 결과를 PENDING으로 요청하면 400이다")
    fun rejectsPendingAsDecision() {
        mvc.perform(put(statusUrl).authorized(admin).json(ReviewReportStatusUpdateRequest(ReportStatus.PENDING)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-5"))
    }

    @Test
    @DisplayName("처리 상태를 누락하면 400이다")
    fun requiresDecision() {
        mvc.perform(put(statusUrl).authorized(admin).json(ReviewReportStatusUpdateRequest(null)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["{", "{\"status\":\"UNKNOWN\"}"])
    @DisplayName("처리 요청의 잘못된 JSON·enum은 400이다")
    fun rejectsMalformedDecision(body: String) {
        mvc.perform(put(statusUrl).authorized(admin).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-2"))
    }

    @Test
    @DisplayName("목록 조회의 잘못된 상태 enum은 400이다")
    fun rejectsInvalidStatusFilter() {
        mvc.perform(get(listUrl).authorized(admin).param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-0"))
    }

    @Test
    @DisplayName("존재하지 않는 신고 처리는 404이다")
    fun rejectsMissingReport() {
        mvc.perform(put("$listUrl/${Long.MAX_VALUE}/status").authorized(admin)
            .json(ReviewReportStatusUpdateRequest(ReportStatus.REJECTED)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-5"))
    }

    @Test
    @DisplayName("이미 처리되어 대기 신고가 없는 리뷰의 재처리는 409이다")
    fun rejectsAlreadyHandledReport() {
        reports.findById(reportId).orElseThrow().changeStatus(ReportStatus.REJECTED)
        flushAndClear()
        mvc.perform(put(statusUrl).authorized(admin).json(ReviewReportStatusUpdateRequest(ReportStatus.APPROVED)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-4"))
    }

    private fun addSecondPendingReport() {
        em.persist(ReviewReport(reviews.findById(reviewId).orElseThrow(), anotherReporter, "추가 신고"))
        flushAndClear()
    }

    private fun flushAndClear() {
        em.flush()
        em.clear()
    }

    private fun requestFor(action: String): MockHttpServletRequestBuilder = when (action) {
        "create" -> post(createUrl).json(ReviewReportCreateRequest("신고"))
        "list" -> get(listUrl)
        "update" -> put(statusUrl).json(ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))
        else -> error("지원하지 않는 요청: $action")
    }

    private fun MockHttpServletRequestBuilder.authorized(user: User) =
        header("Authorization", "Bearer ${jwt.generateAccessToken(user.id, user.role)}")

    private fun MockHttpServletRequestBuilder.json(value: Any) =
        contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(value))
}
