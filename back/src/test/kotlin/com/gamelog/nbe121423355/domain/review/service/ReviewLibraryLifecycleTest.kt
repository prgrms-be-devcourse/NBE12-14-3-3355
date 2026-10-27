package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportCreateRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportStatusUpdateRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameSearchRequest
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.domain.usergame.service.UserGameProfileService
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class ReviewLibraryLifecycleTest {
    @Autowired private lateinit var gameRecordReviewService: GameRecordReviewService
    @Autowired private lateinit var reports: ReviewReportService
    @Autowired private lateinit var library: UserGameService
    @Autowired private lateinit var profile: UserGameProfileService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var games: GameRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var records: UserGameRepository
    @Autowired private lateinit var favorites: UserFavoriteGameRepository
    @Autowired private lateinit var em: EntityManager

    private var userId = 0L
    private var gameId = 0L

    @BeforeEach
    fun setUp() {
        userId = requireNotNull(users.save(User("review-library", "review-library@example.com", "password")).id)
        gameId = requireNotNull(games.save(Game.createFromIgdb(IgdbGameResponse(
            id = 990001L,
            name = "review-library",
            summary = "test",
            cover = null,
            first_release_date = null,
            rating = null,
            involved_companies = mutableListOf(),
            genres = mutableListOf(),
            platforms = mutableListOf(),
            collections = mutableListOf(),
        ))).id)
    }

    private fun record(playing: Boolean) = UserGameReqBody(
        playStatus = null,
        isPlaying = playing,
        isBacklog = false,
        isWishlist = false,
        isLiked = true,
        platformId = null,
        playTimeHours = null,
        finishTimeHours = null,
        masterTimeHours = null,
        startedAt = null,
        completedAt = null,
        lastPlayedAt = null,
    )

    private fun rating() = ReviewSaveRequest(BigDecimal("4.0"), null, false)

    private fun reload(): UserGame {
        em.flush()
        em.clear()
        return requireNotNull(records.findByUser_IdAndGame_Id(userId, gameId))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @DisplayName("별점만 또는 본문만 작성해도 라이브러리에 포함되고 응답에도 반영된다")
    fun reviewOnlyIsIncluded(ratingOnly: Boolean) {
        val request = if (ratingOnly) rating() else ReviewSaveRequest(null, "본문만 작성", false)
        val response = gameRecordReviewService.saveDetailedReview(
            userId, gameId, DetailedReviewSaveRequest(record(false), request),
        )

        assertThat(requireNotNull(response.userGame).inLibrary).isTrue()
        assertThat(reload().isInLibrary).isTrue()
        assertThat(library.getUserGameList(userId, UserGameSearchRequest()).totalElements).isEqualTo(1L)
    }

    @Test
    @DisplayName("리뷰만 있는 게임의 리뷰를 삭제하면 라이브러리와 인생게임에서 제외되고 재작성 시 복원된다")
    fun deleteAndRestoreReviewOnly() {
        val response = gameRecordReviewService.saveDetailedReview(
            userId, gameId, DetailedReviewSaveRequest(record(false), rating()),
        )
        val recordId = requireNotNull(response.userGame).id
        val review = requireNotNull(response.review)
        val reviewId = requireNotNull(review.reviewId)
        profile.updateFavoriteGames(userId, listOf(gameId))

        gameRecordReviewService.deleteReview(userId, reviewId, review.version)

        val saved = reload()
        assertThat(saved.isInLibrary).isFalse()
        assertThat(saved.isLiked).isTrue()
        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(userId)).isEmpty()
        assertThat(reviews.findById(reviewId).orElseThrow().rating).isEqualByComparingTo("4.0")

        val restored = gameRecordReviewService.saveReview(userId, recordId, rating())

        assertThat(restored.reviewId).isEqualTo(reviewId)
        assertThat(reload().isInLibrary).isTrue()
        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(userId)).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @DisplayName("신고 승인으로 리뷰가 숨겨지면 다른 플레이 기록의 유무에 따라 라이브러리와 인생게임을 갱신한다")
    fun hidingReviewRecalculatesMembership(playing: Boolean) {
        val response = gameRecordReviewService.saveDetailedReview(
            userId, gameId, DetailedReviewSaveRequest(record(playing), rating()),
        )
        profile.updateFavoriteGames(userId, listOf(gameId))
        val reporterId = requireNotNull(users.save(User("reporter", "reporter@example.com", "password")).id)
        val reviewId = requireNotNull(requireNotNull(response.review).reviewId)
        val report = reports.createReport(reporterId, reviewId, ReviewReportCreateRequest("신고 테스트"))

        reports.updateStatus(requireNotNull(report.reportId), ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))

        assertThat(reload().isInLibrary).isEqualTo(playing)
        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(userId)).hasSize(if (playing) 1 else 0)
        assertThat(reviews.findById(reviewId).orElseThrow().status).isEqualTo(ReviewStatus.HIDDEN_BY_ADMIN)
    }

    @ParameterizedTest
    @ValueSource(strings = ["played", "playing", "wishlist", "backlog", "liked", "record"])
    @DisplayName("게임 상태나 기록을 지워도 활성 리뷰가 남으면 라이브러리와 인생게임이 유지된다")
    fun activeReviewSurvivesGameRecordChanges(route: String) {
        gameRecordReviewService.saveDetailedReview(
            userId, gameId, DetailedReviewSaveRequest(record(true), rating()),
        )
        profile.updateFavoriteGames(userId, listOf(gameId))
        library.changePlaying(userId, gameId, false)

        when (route) {
            "played" -> library.changePlayed(userId, gameId, null)
            "playing" -> library.changePlaying(userId, gameId, false)
            "wishlist" -> library.changeWishlist(userId, gameId, false)
            "backlog" -> library.changeBacklog(userId, gameId, false)
            "liked" -> library.changeLiked(userId, gameId, false)
            "record" -> library.updatePlayRecord(userId, gameId, record(false))
            else -> error("지원하지 않는 테스트 경로: $route")
        }

        assertThat(reload().isInLibrary).isTrue()
        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(userId)).hasSize(1)
    }

    @Test
    @DisplayName("기존 기록을 모두 지우면서 새 리뷰를 작성해도 인생게임이 중간에 해제되지 않는다")
    fun replacingRecordsWithNewReviewKeepsFavorite() {
        library.addOrUpdateGameToLibrary(userId, gameId, record(true))
        profile.updateFavoriteGames(userId, listOf(gameId))

        val response = gameRecordReviewService.saveDetailedReview(
            userId, gameId, DetailedReviewSaveRequest(record(false), rating()),
        )

        assertThat(requireNotNull(response.userGame).inLibrary).isTrue()
        assertThat(reload().isInLibrary).isTrue()
        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(userId)).hasSize(1)
    }

    @Test
    @DisplayName("빈 리뷰 요청은 기존 활성 리뷰를 유지하고 게임 기록을 비워도 인생게임을 유지한다")
    fun emptyReviewRequestPreservesReviewAndFavorite() {
        gameRecordReviewService.saveDetailedReview(
            userId, gameId, DetailedReviewSaveRequest(record(true), rating()),
        )
        profile.updateFavoriteGames(userId, listOf(gameId))

        val response = gameRecordReviewService.saveDetailedReview(
            userId, gameId,
            DetailedReviewSaveRequest(record(false), ReviewSaveRequest(null, " ", false)),
        )

        assertThat(requireNotNull(response.review).rating).isEqualByComparingTo("4.0")
        assertThat(reload().isInLibrary).isTrue()
        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(userId)).hasSize(1)
    }

    @Test
    @DisplayName("별점을 지우고 본문만 남겨도 라이브러리에는 유지된다")
    fun editingRatingToContentOnlyKeepsLibrary() {
        val response = gameRecordReviewService.saveDetailedReview(
            userId, gameId, DetailedReviewSaveRequest(record(false), rating()),
        )
        val review = requireNotNull(response.review)
        val reviewId = requireNotNull(review.reviewId)

        gameRecordReviewService.updateReview(
            userId, reviewId, ReviewSaveRequest(review.version, null, "본문 유지", false),
        )

        assertThat(reload().isInLibrary).isTrue()
        assertThat(reviews.findById(reviewId).orElseThrow().rating).isNull()
    }
}
