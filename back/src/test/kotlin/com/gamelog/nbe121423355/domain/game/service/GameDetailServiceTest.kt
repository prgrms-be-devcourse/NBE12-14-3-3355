package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.GamePlatform
import com.gamelog.nbe121423355.domain.game.entity.GameSeries
import com.gamelog.nbe121423355.domain.game.entity.GameSeriesGame
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.Platform
import com.gamelog.nbe121423355.domain.game.repository.GameGenreRepository
import com.gamelog.nbe121423355.domain.game.repository.GamePlatformRepository
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.GameSeriesGameRepository
import com.gamelog.nbe121423355.domain.game.repository.GameSeriesRepository
import com.gamelog.nbe121423355.domain.game.repository.GenreRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class GameDetailServiceTest {
    @Autowired
    private lateinit var gameDetailService: GameDetailService

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var genreRepository: GenreRepository

    @Autowired
    private lateinit var platformRepository: PlatformRepository

    @Autowired
    private lateinit var gameSeriesRepository: GameSeriesRepository

    @Autowired
    private lateinit var gameGenreRepository: GameGenreRepository

    @Autowired
    private lateinit var gamePlatformRepository: GamePlatformRepository

    @Autowired
    private lateinit var gameSeriesGameRepository: GameSeriesGameRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    private lateinit var game: Game
    private val gameId: Long
        get() = requireNotNull(game.id) { "저장된 상세 조회 게임의 ID가 필요합니다." }

    @BeforeEach
    fun setUp() {
        val igdbGame = IgdbGameResponse(
            id = 1942L,
            name = "The Witcher 3: Wild Hunt",
            summary = "오픈 월드 액션 RPG",
            cover = IgdbGameResponse.Cover(
                id = 480513L,
                url = "//images.igdb.com/igdb/image/upload/t_thumb/test.jpg",
            ),
            first_release_date = 1431993600L,
            rating = BigDecimal("93.78"),
            involved_companies = mutableListOf(
                IgdbGameResponse.InvolvedCompany(
                    id = 1L,
                    developer = true,
                    company = IgdbGameResponse.Company(id = 1L, name = "CD Projekt RED"),
                )
            ),
            genres = mutableListOf(),
            platforms = mutableListOf(),
            collections = mutableListOf(),
        )
        game = gameRepository.save(Game.createFromIgdb(igdbGame))
        val genre = genreRepository.save(Genre.createFromIgdb(12L, "RPG"))
        val platform = platformRepository.save(Platform.createFromIgdb(6L, "PC"))
        val series = gameSeriesRepository.save(GameSeries.createFromIgdb(62L, "The Witcher"))
        gameGenreRepository.save(GameGenre(game, genre))
        gamePlatformRepository.save(GamePlatform(game, platform))
        gameSeriesGameRepository.save(GameSeriesGame(series, game))
    }

    @DisplayName("게임 기본 정보와 연결 정보를 모두 조회한다")
    @Test
    fun getGameDetailReturnsGameInformation() {
        // given: 게임 기본 정보와 장르·플랫폼·시리즈가 저장되어 있을 때
        val savedGameId = gameId

        // when: 저장된 게임 ID로 상세 정보를 조회하면
        val response = gameDetailService.getGameDetail(savedGameId)

        // then: 게임 기본 정보와 연결 정보 및 0으로 초기화된 통계가 반환된다
        assertThat(response.id).isEqualTo(gameId)
        assertThat(response.igdbId).isEqualTo(1942L)
        assertThat(response.title).isEqualTo("The Witcher 3: Wild Hunt")
        assertThat(response.developer).isEqualTo("CD Projekt RED")
        assertThat(response.igdbRating).isEqualByComparingTo("93.78")
        assertThat(response.genres.map { it.name }).containsExactly("RPG")
        assertThat(response.platforms.map { it.name }).containsExactly("PC")
        assertThat(response.series.map { it.name }).containsExactly("The Witcher")
        val statistics = response.statistics
        assertThat(statistics.playedCount).isZero()
        assertThat(statistics.playingCount).isZero()
        assertThat(statistics.backlogCount).isZero()
        assertThat(statistics.wishlistCount).isZero()
        assertThat(statistics.likeCount).isZero()
        assertThat(statistics.averageRating).isEqualByComparingTo("0")
        assertThat(statistics.ratingCount).isZero()
        assertThat(statistics.reviewCount).isZero()
        assertThat(statistics.ratingDistribution).hasSize(10)
            .allSatisfy { item -> assertThat(item.count).isZero() }
        assertThat(statistics.averagePlayTimeHours).isEqualByComparingTo("0")
        assertThat(statistics.playTimeUserCount).isZero()
    }

    @DisplayName("게임의 상태별 사용자 수를 조회한다")
    @Test
    fun getGameDetailReturnsGameStatistics() {
        // given: 서로 다른 상태로 게임을 등록한 사용자들이 있을 때
        saveUserGame("user1@test.com", "user1", PlayStatus.COMPLETED, playing = true, backlog = false, wishlist = false)
        saveUserGame("user2@test.com", "user2", PlayStatus.PLAYED, playing = false, backlog = true, wishlist = false)
        saveUserGame("user3@test.com", "user3", null, playing = false, backlog = false, wishlist = true)
        entityManager.flush()

        // when: 해당 게임의 상세 정보를 조회하면
        val response = gameDetailService.getGameDetail(gameId)

        // then: Played, Playing, Backlog, Wishlist 인원수가 상태별로 집계된다
        val statistics = response.statistics
        assertThat(statistics.playedCount).isEqualTo(2L)
        assertThat(statistics.playingCount).isEqualTo(1L)
        assertThat(statistics.backlogCount).isEqualTo(1L)
        assertThat(statistics.wishlistCount).isEqualTo(1L)
    }

    @DisplayName("플레이타임을 기록한 사용자만 평균과 인원수에 포함한다")
    @Test
    fun getGameDetailReturnsPlayTimeStatistics() {
        // given: 플레이 상태와 관계없이 2시간, 4시간, 0시간 및 미기록 사용자가 있을 때
        val twoHours = saveUserGame(
            "playtime1@test.com", "playtime1", PlayStatus.COMPLETED, playing = false, backlog = false, wishlist = false,
        )
        ReflectionTestUtils.setField(twoHours, "playTimeHours", BigDecimal("2.00"))
        val fourHours = saveUserGame(
            "playtime2@test.com", "playtime2", null, playing = true, backlog = false, wishlist = false,
        )
        ReflectionTestUtils.setField(fourHours, "playTimeHours", BigDecimal("4.00"))
        val zeroHours = saveUserGame(
            "playtime3@test.com", "playtime3", null, playing = false, backlog = false, wishlist = false,
        )
        ReflectionTestUtils.setField(zeroHours, "playTimeHours", BigDecimal("0.00"))
        saveUserGame(
            "playtime4@test.com", "playtime4", PlayStatus.PLAYED, playing = false, backlog = false, wishlist = false,
        )
        entityManager.flush()

        // when: 해당 게임의 상세 정보를 조회하면
        val response = gameDetailService.getGameDetail(gameId)

        // then: null은 제외하고 0시간은 포함해 평균 2시간, 기록 사용자 3명이 반환된다
        assertThat(response.statistics.averagePlayTimeHours).isEqualByComparingTo("2")
        assertThat(response.statistics.playTimeUserCount).isEqualTo(3L)
    }

    @DisplayName("사용자는 있지만 플레이타임 기록이 없으면 0으로 반환한다")
    @Test
    fun getGameDetailReturnsZeroPlayTimeStatisticsWhenAllPlayTimesAreNull() {
        // given: 게임을 등록한 사용자의 플레이타임이 null일 때
        saveUserGame(
            "playtime-empty@test.com", "playtime-empty", PlayStatus.COMPLETED,
            playing = false, backlog = false, wishlist = false,
        )
        entityManager.flush()

        // when: 해당 게임의 상세 정보를 조회하면
        val response = gameDetailService.getGameDetail(gameId)

        // then: 평균 플레이타임과 기록 사용자 수가 모두 0으로 반환된다
        assertThat(response.statistics.averagePlayTimeHours).isEqualByComparingTo("0")
        assertThat(response.statistics.playTimeUserCount).isZero()
    }

    @DisplayName("게임의 평점, 리뷰 수, 좋아요 수와 평점별 분포를 조회한다")
    @Test
    fun getGameDetailReturnsRatingReviewAndLikeStatistics() {
        // given: 평점과 좋아요 상태가 서로 다른 사용자들이 있을 때
        val halfRating = saveUserGame(
            "rating1@test.com", "rating1", null, playing = false, backlog = false, wishlist = false,
        )
        ReflectionTestUtils.setField(halfRating, "liked", true)
        entityManager.persist(Review(halfRating, BigDecimal("0.5"), null, false))
        val fiveRatingWithoutLike = saveUserGame(
            "rating2@test.com", "rating2", null, playing = false, backlog = false, wishlist = false,
        )
        entityManager.persist(Review(fiveRatingWithoutLike, BigDecimal("5.0"), null, false))
        val fiveRatingWithLike = saveUserGame(
            "rating3@test.com", "rating3", null, playing = false, backlog = false, wishlist = false,
        )
        ReflectionTestUtils.setField(fiveRatingWithLike, "liked", true)
        entityManager.persist(Review(fiveRatingWithLike, BigDecimal("5.0"), null, false))
        val contentOnlyReview = saveUserGame(
            "rating-content@test.com", "ratingContent", null, playing = false, backlog = false, wishlist = false,
        )
        entityManager.persist(Review(contentOnlyReview, null, "별점 없이 작성한 리뷰", false))
        val likedWithoutReview = saveUserGame(
            "rating4@test.com", "rating4", null, playing = false, backlog = false, wishlist = false,
        )
        ReflectionTestUtils.setField(likedWithoutReview, "liked", true)
        entityManager.flush()

        // when: 해당 게임의 상세 정보를 조회하면
        val response = gameDetailService.getGameDetail(gameId)

        // then: 별점 전용 기록은 평가 통계와 공개 목록에 포함하고, 리뷰 수는 내용이 있는 기록만 센다
        val statistics = response.statistics
        assertThat(statistics.averageRating).isEqualByComparingTo("3.5")
        assertThat(statistics.ratingCount).isEqualTo(3L)
        assertThat(statistics.reviewCount).isEqualTo(1L)
        assertThat(statistics.likeCount).isEqualTo(3L)
        assertThat(reviewRepository.findByUserGame_Game_Id(gameId, PageRequest.of(0, 20)).content.map { it.content })
            .containsExactlyInAnyOrder(null, null, null, "별점 없이 작성한 리뷰")
        val distribution = statistics.ratingDistribution
        assertThat(distribution).hasSize(10)
        assertThat(distribution.first().rating).isEqualByComparingTo("0.5")
        assertThat(distribution.last().rating).isEqualByComparingTo("5.0")
        assertThat(distribution.map { it.count }).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 2L)
    }

    @DisplayName("존재하지 않는 게임 ID를 조회하면 예외가 발생한다")
    @Test
    fun getGameDetailThrowsExceptionWhenGameDoesNotExist() {
        // given: 존재하지 않는 게임 ID가 주어졌을 때
        val nonexistentGameId = Long.MAX_VALUE

        // when: 해당 게임의 상세 정보를 조회하면
        val throwable = catchThrowable { gameDetailService.getGameDetail(nonexistentGameId) }

        // then: 게임이 존재하지 않는다는 ServiceException이 발생한다
        assertThat(throwable).isInstanceOf(ServiceException::class.java).hasMessage("존재하지 않는 게임입니다.")
    }

    private fun saveUserGame(
        email: String,
        nickname: String,
        playStatus: PlayStatus?,
        playing: Boolean,
        backlog: Boolean,
        wishlist: Boolean,
    ): UserGame {
        val user = User(nickname, email, "password")
        entityManager.persist(user)
        val userGame = UserGame(user, game)
        ReflectionTestUtils.setField(userGame, "playStatus", playStatus)
        ReflectionTestUtils.setField(userGame, "playing", playing)
        ReflectionTestUtils.setField(userGame, "backlog", backlog)
        ReflectionTestUtils.setField(userGame, "wishlist", wishlist)
        entityManager.persist(userGame)
        return userGame
    }
}
