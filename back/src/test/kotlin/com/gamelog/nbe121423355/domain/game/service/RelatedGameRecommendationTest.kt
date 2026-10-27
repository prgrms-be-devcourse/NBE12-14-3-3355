package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class RelatedGameRecommendationTest {
    @Autowired
    private lateinit var gameDetailService: GameDetailService

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var baseGame: Game
    private lateinit var rpg: Genre
    private val baseGameId: Long
        get() = requireNotNull(baseGame.id) { "저장된 연관 추천 기준 게임의 ID가 필요합니다." }

    @BeforeEach
    fun setUp() {
        baseGame = saveGame(1000L, "기준 게임", "90.0")
        rpg = Genre.createFromIgdb(1000L, "RPG")
        entityManager.persist(rpg)
        entityManager.persist(GameGenre(baseGame, rpg))
    }

    @Test
    @DisplayName("기본 설정은 저장된 빈 결과가 있어도 기존 계산을 사용한다")
    fun defaultConfigurationKeepsRealtimeQuery() {
        // given: 계산에 성공한 빈 저장 결과와 실시간 조회에 적합한 후보를 준비한다.
        val calculation = RelatedGameCalculation(baseGame)
        calculation.start(LocalDateTime.now())
        calculation.succeed(LocalDateTime.now())
        entityManager.persist(calculation)
        val candidate = saveCandidate(1090L, "실시간 후보", "80.0")
        entityManager.flush()

        // when: 사전 계산 조회를 켜지 않은 기본 설정으로 연관 추천을 조회한다.
        val result = gameDetailService.getRelatedGames(baseGameId)

        // then: 저장된 빈 결과 대신 기존 실시간 계산의 후보를 반환한다.
        assertThat(result.map { it.id }).containsExactly(candidate.id)
    }

    @Test
    @DisplayName("기준 게임의 긍정 사용자만 후보의 별점과 좋아요에 점수를 더한다")
    fun getRelatedGamesScoresOnlyPositiveUsersInteractions() {
        // given: 긍정 사용자는 B에 별점과 좋아요를 남기고 C는 플레이만 한 상태
        val b = saveCandidate(1001L, "B", "80.0")
        val c = saveCandidate(1002L, "C", "80.0")
        val positiveUser = saveUser(1)
        saveUserGame(positiveUser, baseGame, PlayStatus.COMPLETED, liked = false)
        saveReview(saveUserGame(positiveUser, b, null, liked = true), "4.5")
        saveUserGame(positiveUser, c, PlayStatus.PLAYED, liked = false)
        val otherUser = saveUser(2)
        saveUserGame(otherUser, baseGame, null, liked = false)
        saveReview(saveUserGame(otherUser, c, null, liked = true), "5.0")
        entityManager.flush()

        // when: 기준 게임의 연관 추천을 조회
        val result = gameDetailService.getRelatedGames(baseGameId)

        // then: B는 별점 1.5점과 좋아요 1점이 더해지고 C는 기본 점수만 받는다
        assertThat(result.map { it.id }).containsExactly(b.id, c.id)
        assertThat(result[0].recommendationScore).isEqualByComparingTo("57.5")
        assertThat(result[1].recommendationScore).isEqualByComparingTo("55.0")
    }

    @Test
    @DisplayName("공유 장르와 IGDB 평점으로 후보를 제한하고 최종 점수 상위 5개를 반환한다")
    fun getRelatedGamesReturnsTopFiveAfterFinalScoring() {
        // given: 동일 점수 후보 5개와 공유 장르가 하나 더 많은 후보가 있을 때
        val candidates = (1..5).map { index -> saveCandidate(1010L + index, "후보 $index", "80.0") }
        val twoGenres = saveCandidate(1016L, "장르 2개", "80.0")
        val action = Genre.createFromIgdb(1001L, "액션")
        entityManager.persist(action)
        entityManager.persist(GameGenre(baseGame, action))
        entityManager.persist(GameGenre(twoGenres, action))
        saveCandidate(1017L, "IGDB 평점 미달", "69.0")
        saveGame(1018L, "공유 장르 없음", "99.0")
        entityManager.flush()

        // when: 기준 게임의 연관 추천을 조회
        val result = gameDetailService.getRelatedGames(baseGameId)

        // then: 장르 가산점이 적용된 후보가 먼저 나오고 동점은 ID순으로 4개만 나온다
        assertThat(result.map { it.id }).containsExactly(
            twoGenres.id, candidates[0].id, candidates[1].id, candidates[2].id, candidates[3].id,
        )
        assertThat(result[0].recommendationScore).isEqualByComparingTo("70.0")
        assertThat(requireNotNull(result[0].genres).map { it.name }).containsExactlyInAnyOrder("RPG", "액션")
        assertThat(requireNotNull(result[1].genres).map { it.name }).containsExactly("RPG")
    }

    @Test
    @DisplayName("별점 5개 미만은 포함하고 5개 이상이면 평균 3.5 이상만 포함한다")
    fun getRelatedGamesAppliesActualRatingFilter() {
        // given: 후보마다 실제 별점 개수와 평균이 다를 때
        val fewRatings = saveCandidate(1021L, "별점 4개", "80.0")
        val lowAverage = saveCandidate(1022L, "낮은 평균", "80.0")
        val highAverage = saveCandidate(1023L, "높은 평균", "80.0")
        for (index in 1..5) {
            if (index <= 4) {
                saveReview(saveUserGame(saveUser(index), fewRatings, null, liked = false), "1.0")
            }
            saveReview(saveUserGame(saveUser(index + 10), lowAverage, null, liked = false), "1.0")
            saveReview(saveUserGame(saveUser(index + 20), highAverage, null, liked = false), "4.0")
        }
        val contentOnly = saveUserGame(saveUser(40), fewRatings, null, liked = false)
        entityManager.persist(Review(contentOnly, null, "별점 없는 리뷰", false))
        entityManager.flush()

        // when: 기준 게임의 연관 추천을 조회
        val result = gameDetailService.getRelatedGames(baseGameId)

        // then: 별점 없는 리뷰는 개수에서 빠지고 평균 1점인 별점 5개 후보는 제외된다
        assertThat(result.map { it.id }).containsExactly(fewRatings.id, highAverage.id)
    }

    @Test
    @DisplayName("존재하지 않는 기준 게임의 연관 추천 조회는 실패한다")
    fun getRelatedGamesThrowsWhenBaseGameDoesNotExist() {
        // given: 저장되지 않은 게임 ID
        val missingGameId = Long.MAX_VALUE

        // when: 연관 추천을 조회
        val throwable = catchThrowable { gameDetailService.getRelatedGames(missingGameId) }

        // then: 게임이 없다는 예외가 발생한다
        assertThat(throwable).isInstanceOf(ServiceException::class.java).hasMessage("존재하지 않는 게임입니다.")
    }

    private fun saveCandidate(igdbId: Long, title: String, igdbRating: String): Game {
        val candidate = saveGame(igdbId, title, igdbRating)
        entityManager.persist(GameGenre(candidate, rpg))
        return candidate
    }

    private fun saveGame(igdbId: Long, title: String, igdbRating: String): Game {
        val game = Game.createFromIgdb(
            IgdbGameResponse(
                id = igdbId,
                name = title,
                summary = null,
                cover = null,
                first_release_date = null,
                rating = BigDecimal(igdbRating),
                involved_companies = mutableListOf(),
                genres = mutableListOf(),
                platforms = mutableListOf(),
                collections = mutableListOf(),
            )
        )
        entityManager.persist(game)
        return game
    }

    private fun saveUser(number: Int): User {
        val user = User("related$number", "related$number@test.com", "password")
        entityManager.persist(user)
        return user
    }

    private fun saveUserGame(user: User, game: Game, status: PlayStatus?, liked: Boolean): UserGame {
        val userGame = UserGame(user, game)
        if (status != null) {
            userGame.changePlayStatus(status)
        }
        userGame.changeLiked(liked)
        entityManager.persist(userGame)
        return userGame
    }

    private fun saveReview(userGame: UserGame, rating: String) {
        entityManager.persist(Review(userGame, BigDecimal(rating), null, false))
    }
}
