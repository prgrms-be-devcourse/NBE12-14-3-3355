package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.review.service.GameRecordReviewService
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class UserGameReviewStatisticsTest {
    @Autowired private lateinit var library: UserGameService
    @Autowired private lateinit var gameRecordReviewService: GameRecordReviewService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var games: GameRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var records: UserGameRepository
    @Autowired private lateinit var em: EntityManager
    private var userId = 0L
    private lateinit var genre: Genre
    private var nextGameId = 991000L

    @BeforeEach
    fun setUp() {
        userId = checkNotNull(users.save(User("review-stats", "review-stats@example.com", "password")).id)
        genre = Genre.createFromIgdb(991000L, "통계 테스트 장르")
        em.persist(genre)
    }

    private fun save(status: PlayStatus?, hours: String?, rating: String?, content: String?): UserGame {
        val game = games.save(Game.createFromIgdb(IgdbGameResponse(nextGameId++, "game-$nextGameId", "test",
            null, null, null, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())))
        em.persist(GameGenre(game, genre))
        val record = UserGameReqBody(
            playStatus = status,
            isPlaying = false,
            isBacklog = false,
            isWishlist = false,
            isLiked = false,
            platformId = null,
            playTimeHours = hours?.let(::BigDecimal),
            finishTimeHours = null,
            masterTimeHours = null,
            startedAt = null,
            completedAt = null,
            lastPlayedAt = null,
        )
        val review = if (rating == null && content == null) null
        else ReviewSaveRequest(rating?.let(::BigDecimal), content, false)
        val response = gameRecordReviewService.saveDetailedReview(userId, checkNotNull(game.id), DetailedReviewSaveRequest(record, review))
        return records.findById(checkNotNull(response.userGame).id).orElseThrow()
    }

    private fun review(game: UserGame): Review { return reviews.findByUserGame_Id(checkNotNull(game.id)).orElseThrow() }

    @Test
    @DisplayName("플레이 상태 없이 별점이나 양수 시간이 있어도 집계하고 본문만 있는 게임과 0시간은 제외한다")
    fun statisticsUseRatingsAndPositiveHoursWithoutPlayStatus() {
        val rated = save(null, null, "4.0", null)
        val timed = save(null, "10.0", null, null)
        val both = save(null, "5.0", "2.0", null)
        val contentOnly = save(null, null, null, "기대평")
        val zeroOnly = save(null, "0.0", null, null)
        val zeroRated = save(null, "0.0", "3.0", null)
        val hidden = save(null, "20.0", "5.0", null)
        review(hidden).hideByAdmin()
        library.synchronizeLibraryState(hidden)
        val deleted = save(null, "30.0", "5.0", null)
        val deletedReview = review(deleted)
        gameRecordReviewService.deleteReview(userId, checkNotNull(deletedReview.id), deletedReview.version)
        val completed = save(PlayStatus.COMPLETED, null, null, null)
        val wish = save(null, null, null, null)
        library.changeWishlist(userId, checkNotNull(wish.game.id), true)
        val expectedPlayed = listOf(checkNotNull(rated.id), checkNotNull(timed.id), checkNotNull(both.id), checkNotNull(zeroRated.id), checkNotNull(hidden.id), checkNotNull(deleted.id), checkNotNull(completed.id))
        val scatterGameId = checkNotNull(both.game.id)
        em.flush()
        em.clear()

        val profile = library.profileTab(userId, userId)
        assertThat(profile.stats.playedGameCount).isEqualTo(7L)
        assertThat(profile.stats.averageRating).isEqualByComparingTo("3.0")
        assertThat(profile.stats.totalPlayTime).isEqualByComparingTo("65.0")
        assertThat(profile.scatterData.map { it.gameId }).containsExactly(scatterGameId)
        assertThat(records.findPlayedGames(userId).map { it.id }).containsExactlyInAnyOrderElementsOf(expectedPlayed)
        assertThat(records.findGenreDistribution(userId).map { it.gameCount }).containsExactly(7L)
        assertThat(profile.tasteResponse.completion.ratio).isEqualByComparingTo("0.1429")
        assertThat(profile.tasteResponse.longPlay.ratio).isEqualByComparingTo("0.75")
        assertThat(records.findById(checkNotNull(contentOnly.id)).orElseThrow().inLibrary).isTrue()
        assertThat(records.findById(checkNotNull(zeroOnly.id)).orElseThrow().inLibrary).isFalse()
    }

    @Test
    @DisplayName("삭제·숨김 리뷰의 별점은 정렬에서 제외하되 게임과 페이지 전체 건수는 유지한다")
    fun ratingSortKeepsGamesWithInvisibleOrMissingReviews() {
        val active = save(null, null, "2.0", null)
        val deleted = save(null, "10", "5.0", null)
        val deletedReview = review(deleted)
        gameRecordReviewService.deleteReview(userId, checkNotNull(deletedReview.id), deletedReview.version)
        val hidden = save(null, "5", "4.0", null)
        review(hidden).hideByAdmin()
        library.synchronizeLibraryState(hidden)
        val unrated = save(null, "7", null, null)
        val expectedGames = listOf(checkNotNull(active.game.id), checkNotNull(deleted.game.id), checkNotNull(hidden.game.id), checkNotNull(unrated.game.id))
        val firstGame = checkNotNull(active.game.id)
        em.flush()
        em.clear()
        val request = UserGameSearchRequest()
        request.sort = UserGameSort.RATING
        request.size = 1
        val firstPage = library.getUserGameList(userId, request)
        assertThat(firstPage.totalElements).isEqualTo(4L)
        assertThat(firstPage.content.map { it.gameId }).containsExactly(firstGame)
        request.size = 60
        assertThat(library.getUserGameList(userId, request).content.map { it.gameId })
            .containsExactlyInAnyOrderElementsOf(expectedGames)
    }

    @Test
    @DisplayName("status가 null인 기존 리뷰도 라이브러리·정렬·평균·산점도에서 활성 리뷰로 취급한다")
    fun legacyNullStatusRemainsActive() {
        val legacy = save(null, "5", "4.0", null)
        save(null, null, "2.0", null)
        val recordId = checkNotNull(checkNotNull(legacy.id))
        val gameId = checkNotNull(checkNotNull(legacy.game.id))
        em.flush()
        em.createQuery("UPDATE Review r SET r.status = NULL WHERE r.userGame.id = :id")
            .setParameter("id", recordId).executeUpdate()
        em.clear()
        library.changeLiked(userId, gameId, true)
        assertThat(records.findById(recordId).orElseThrow().inLibrary).isTrue()
        val profile = library.profileTab(userId, userId)
        assertThat(profile.stats.playedGameCount).isEqualTo(2L)
        assertThat(profile.stats.averageRating).isEqualByComparingTo("3.0")
        assertThat(profile.scatterData.map { it.gameId }).containsExactly(gameId)
        val request = UserGameSearchRequest()
        request.sort = UserGameSort.RATING
        assertThat(library.getUserGameList(userId, request).content.first().gameId).isEqualTo(gameId)
    }

    @Test
    @DisplayName("활성 별점을 본문만 있는 리뷰로 바꾸면 라이브러리는 유지하고 플레이 통계에서는 제외한다")
    fun contentOnlyAfterEditIsNotPlayed() {
        val rated = save(null, null, "4.0", null)
        val ratedReview = review(rated)
        gameRecordReviewService.updateReview(
            userId,
            checkNotNull(ratedReview.id),
            ReviewSaveRequest(ratedReview.version, null, "본문만 남김", false)
        )
        em.flush()
        em.clear()
        val profile = library.profileTab(userId, userId)
        assertThat(profile.stats.playedGameCount).isZero()
        assertThat(profile.stats.averageRating).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(profile.scatterData).isEmpty()
        assertThat(library.getUserGameList(userId, UserGameSearchRequest()).totalElements).isEqualTo(1L)
    }
}
