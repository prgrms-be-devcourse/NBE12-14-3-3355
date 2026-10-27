package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
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

/** JWT 필터부터 요청 검증·실제 서비스·MySQL 저장 및 JSON 응답까지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class ReviewControllerTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var records: UserGameRepository

    private lateinit var owner: User
    private lateinit var other: User
    private lateinit var game: Game
    private lateinit var freshGame: Game
    private lateinit var record: UserGame
    private lateinit var emptyRecord: UserGame
    private var reviewId = 0L
    private var version = 0L
    private val ownerId get() = checkNotNull(owner.id)
    private val gameId get() = checkNotNull(game.id)
    private val freshGameId get() = checkNotNull(freshGame.id)
    private val recordId get() = checkNotNull(record.id)
    private val emptyRecordId get() = checkNotNull(emptyRecord.id)

    @BeforeEach
    fun setUp() {
        owner = User("review-api-owner", "review-api-owner@test.com", "password")
        other = User("review-api-other", "review-api-other@test.com", "password")
        game = game(987501L, "리뷰 API 게임")
        freshGame = game(987502L, "새 리뷰 게임")
        listOf(owner, other, game, freshGame).forEach(em::persist)
        record = UserGame(owner, game)
        emptyRecord = UserGame(owner, freshGame)
        em.persist(record)
        em.persist(emptyRecord)
        val review = Review(record, BigDecimal("4.0"), "기존 리뷰", false)
        em.persist(review)
        record.updateLibraryStatus(hasActiveReview = true)
        em.flush()
        reviewId = checkNotNull(review.id)
        version = checkNotNull(review.version)
        em.clear()
    }

    @Test
    @DisplayName("별점만 저장해도 리뷰 응답과 라이브러리 포함 상태가 저장된다")
    fun savesRatingOnlyReview() {
        assertThat(records.findById(emptyRecordId).orElseThrow().isInLibrary).isFalse()
        mvc.perform(put("/api/v1/user-games/$emptyRecordId/reviews").authorized()
            .json(reviewBody(content = null)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-6"))
            .andExpect(jsonPath("$.data.reviewId").isNumber)
            .andExpect(jsonPath("$.data.version").isNumber)
            .andExpect(jsonPath("$.data.userGameId").value(emptyRecordId))
            .andExpect(jsonPath("$.data.gameId").value(freshGameId))
            .andExpect(jsonPath("$.data.userId").value(ownerId))
            .andExpect(jsonPath("$.data.rating").value(4.5))
            .andExpect(jsonPath("$.data.content").value(nullValue()))
        em.flush()
        em.clear()
        assertThat(records.findById(emptyRecordId).orElseThrow().isInLibrary).isTrue()
        assertThat(reviews.findByUserGame_Id(emptyRecordId).orElseThrow().rating)
            .isEqualByComparingTo("4.5")
    }

    @Test
    @DisplayName("통합 저장 API는 게임 기록과 리뷰를 저장하고 두 응답을 반환한다")
    fun savesGameRecordAndReviewTogether() {
        mvc.perform(put("/api/v1/games/$freshGameId/reviews").authorized()
            .json(DetailedReviewSaveRequest(gameRecord(), reviewBody())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-7"))
            .andExpect(jsonPath("$.data.userGame.gameId").value(freshGameId))
            .andExpect(jsonPath("$.data.userGame.playTimeHours").value(12.5))
            .andExpect(jsonPath("$.data.userGame.inLibrary").value(true))
            .andExpect(jsonPath("$.data.review.userGameId").value(emptyRecordId))
            .andExpect(jsonPath("$.data.review.content").value("새 리뷰"))
        em.flush()
        em.clear()
        assertThat(records.findById(emptyRecordId).orElseThrow().playTimeHours)
            .isEqualByComparingTo("12.5")
        assertThat(reviews.findByUserGame_Id(emptyRecordId)).isPresent()
    }

    @Test
    @DisplayName("게임 기록이 없는 게임도 통합 API에서 기록과 리뷰를 새로 생성한다")
    fun createsMissingGameRecordThroughDetailedSave() {
        val newGame = game(987503L, "기록 없는 게임")
        em.persist(newGame)
        em.flush()
        val newGameId = checkNotNull(newGame.id)
        assertThat(records.findByUser_IdAndGame_Id(ownerId, newGameId)).isNull()
        mvc.perform(put("/api/v1/games/$newGameId/reviews").authorized()
            .json(DetailedReviewSaveRequest(gameRecord(), reviewBody())))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-7"))
            .andExpect(jsonPath("$.data.userGame.gameId").value(newGameId))
            .andExpect(jsonPath("$.data.review.gameId").value(newGameId))
        em.flush()
        em.clear()
        val created = checkNotNull(records.findByUser_IdAndGame_Id(ownerId, newGameId))
        assertThat(created.isInLibrary).isTrue()
        assertThat(reviews.findByUserGame_Id(checkNotNull(created.id))).isPresent()
    }

    @Test
    @DisplayName("인증한 사용자는 내 상세 리뷰와 게임 기록을 조회한다")
    fun getsMyDetailedReview() {
        mvc.perform(get("/api/v1/games/$gameId/reviews/me").authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-8"))
            .andExpect(jsonPath("$.data.userGame.id").value(recordId))
            .andExpect(jsonPath("$.data.review.reviewId").value(reviewId))
            .andExpect(jsonPath("$.data.review.version").value(version))
    }

    @Test
    @DisplayName("인증한 사용자는 리뷰 단건을 조회하고 편집에 필요한 버전을 받는다")
    fun getsSingleReview() {
        mvc.perform(get("/api/v1/reviews/$reviewId").authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.reviewId").value(reviewId))
            .andExpect(jsonPath("$.data.version").value(version))
            .andExpect(jsonPath("$.data.content").value("기존 리뷰"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["game", "user"])
    @DisplayName("게임·사용자 리뷰 목록은 비로그인 조회와 페이징 응답을 지원한다")
    fun listsPublicReviews(target: String) {
        val url = if (target == "game") "/api/v1/games/$gameId/reviews"
            else "/api/v1/users/$ownerId/reviews"
        mvc.perform(get(url).param("page", "0").param("size", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value(if (target == "game") "200-2" else "200-3"))
            .andExpect(jsonPath("$.data.reviews[0].reviewId").value(reviewId))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @ParameterizedTest
    @ValueSource(strings = ["save", "update"])
    @DisplayName("리뷰 저장·수정 API는 현재 버전으로 수정하면 증가한 버전을 반환한다")
    fun updatesReview(action: String) {
        val url = if (action == "save") "/api/v1/user-games/$recordId/reviews"
            else "/api/v1/reviews/$reviewId"
        mvc.perform(put(url).authorized().json(reviewBody(version, "수정한 리뷰")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value(if (action == "save") "200-6" else "200-4"))
            .andExpect(jsonPath("$.data.reviewId").value(reviewId))
            .andExpect(jsonPath("$.data.version").value(version + 1))
            .andExpect(jsonPath("$.data.content").value("수정한 리뷰"))
        em.flush()
        em.clear()
        assertThat(reviews.findById(reviewId).orElseThrow().content).isEqualTo("수정한 리뷰")
    }

    @Test
    @DisplayName("리뷰 삭제 API는 소프트 삭제하고 다른 기록이 없으면 라이브러리에서 제외한다")
    fun deletesReview() {
        assertThat(records.findById(recordId).orElseThrow().isInLibrary).isTrue()
        mvc.perform(delete("/api/v1/reviews/$reviewId").param("version", version.toString()).authorized())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-5"))
        em.flush()
        em.clear()
        assertThat(reviews.findById(reviewId).orElseThrow().status).isEqualTo(ReviewStatus.DELETED_BY_USER)
        assertThat(records.findById(recordId).orElseThrow().isInLibrary).isFalse()
        mvc.perform(get("/api/v1/reviews/$reviewId").authorized())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-3"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["save", "detailed", "update", "delete", "mine"])
    @DisplayName("비로그인 사용자의 저장·수정·삭제·내 리뷰 조회는 401이다")
    fun rejectsUnauthenticatedRequests(action: String) {
        mvc.perform(requestFor(action))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-1"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["save", "update", "delete"])
    @DisplayName("다른 사용자 기록에 리뷰를 저장하거나 수정·삭제하면 403이다")
    fun rejectsAnotherOwner(action: String) {
        mvc.perform(requestFor(action).authorized(other))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["save", "update", "delete", "detailed"])
    @DisplayName("현재 버전보다 오래된 요청은 HTTP 409와 409-6 코드를 반환한다")
    fun rejectsStaleVersion(action: String) {
        // DB 버전을 먼저 올려 이전 화면이 보유한 버전과 차이를 만든다.
        reviews.findById(reviewId).orElseThrow().edit(BigDecimal("5.0"), "다른 화면의 최신 리뷰", false)
        em.flush()
        em.clear()
        mvc.perform(requestFor(action).authorized())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-6"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["update", "delete"])
    @DisplayName("기존 리뷰 수정·삭제에서 버전을 생략하면 409이다")
    fun requiresVersionForExistingReview(action: String) {
        val request = if (action == "update") put("/api/v1/reviews/$reviewId").json(reviewBody())
            else delete("/api/v1/reviews/$reviewId")
        mvc.perform(request.authorized())
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-6"))
    }

    @Test
    @DisplayName("통합 저장에서도 중첩된 리뷰의 별점 검증을 적용한다")
    fun validatesNestedReview() {
        mvc.perform(put("/api/v1/games/$freshGameId/reviews").authorized()
            .json(DetailedReviewSaveRequest(gameRecord(), ReviewSaveRequest(BigDecimal("5.5"), "내용", false))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
        assertThat(reviews.findByUserGame_Id(emptyRecordId)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["0.0", "4.3", "5.5"])
    @DisplayName("범위 또는 0.5 단위를 벗어난 별점은 요청 검증에서 400이다")
    fun rejectsInvalidRatings(rating: String) {
        mvc.perform(put("/api/v1/user-games/$emptyRecordId/reviews").authorized()
            .json(ReviewSaveRequest(BigDecimal(rating), "새 리뷰", false)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
        assertThat(reviews.findByUserGame_Id(emptyRecordId)).isEmpty()
    }

    @Test
    @DisplayName("별점과 내용이 모두 없으면 빈 리뷰 저장을 거절한다")
    fun rejectsEmptyReview() {
        mvc.perform(put("/api/v1/user-games/$emptyRecordId/reviews").authorized()
            .json(ReviewSaveRequest(null, "   ", false)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-4"))
    }

    @Test
    @DisplayName("통합 저장에서 필수 게임 기록을 누락하면 400이다")
    fun requiresGameRecordInDetailedSave() {
        mvc.perform(put("/api/v1/games/$freshGameId/reviews").authorized()
            .json(DetailedReviewSaveRequest(null, reviewBody())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }

    @Test
    @DisplayName("잘못된 JSON은 400과 공통 오류 응답을 반환한다")
    fun rejectsMalformedJson() {
        mvc.perform(put("/api/v1/user-games/$emptyRecordId/reviews").authorized()
            .contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-2"))
    }

    @Test
    @DisplayName("존재하지 않는 리뷰 조회는 404이다")
    fun rejectsMissingReview() {
        mvc.perform(get("/api/v1/reviews/${Long.MAX_VALUE}").authorized())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-3"))
    }

    private fun requestFor(action: String): MockHttpServletRequestBuilder = when (action) {
        "save" -> put("/api/v1/user-games/$recordId/reviews").json(reviewBody(version))
        "update" -> put("/api/v1/reviews/$reviewId").json(reviewBody(version))
        "delete" -> delete("/api/v1/reviews/$reviewId").param("version", version.toString())
        "detailed" -> put("/api/v1/games/$gameId/reviews")
            .json(DetailedReviewSaveRequest(gameRecord(), reviewBody(version)))
        "mine" -> get("/api/v1/games/$gameId/reviews/me")
        else -> error("지원하지 않는 요청: $action")
    }

    private fun MockHttpServletRequestBuilder.authorized(user: User = owner) =
        header("Authorization", "Bearer ${jwt.generateAccessToken(user.id, user.role)}")

    private fun MockHttpServletRequestBuilder.json(value: Any) =
        contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(value))

    private fun reviewBody(version: Long? = null, content: String? = "새 리뷰") =
        ReviewSaveRequest(version, BigDecimal("4.5"), content, false)

    private fun gameRecord() = UserGameReqBody(
        null, false, false, false, false, null, BigDecimal("12.5"), null, null, null, null, null,
    )

    private fun game(id: Long, title: String) = Game.createFromIgdb(IgdbGameResponse(
        id, title, "test", null, null, null, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
    ))
}
