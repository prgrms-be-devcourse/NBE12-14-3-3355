package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class GameStatisticsRepositoryTest {
    @Autowired
    private lateinit var gameStatisticsRepository: GameStatisticsRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userGameRepository: UserGameRepository

    @Test
    @DisplayName("좋아요 수와 게임 ID를 기준으로 인기 게임을 정렬한다")
    fun findPopularGamesOrdersByLikeCountAndGameId() {
        // given: 좋아요 수가 같거나 서로 다른 게임과 좋아요가 없는 게임을 준비한다.
        val firstGame = saveGame(1001L, "첫 번째 게임")
        val secondGame = saveGame(1002L, "두 번째 게임")
        val thirdGame = saveGame(1003L, "세 번째 게임")
        val unlikedGame = saveGame(1004L, "좋아요 없는 게임")
        val firstUser = saveUser(1)
        val secondUser = saveUser(2)
        val thirdUser = saveUser(3)
        saveUserGame(firstUser, firstGame, liked = true)
        saveUserGame(secondUser, firstGame, liked = true)
        saveUserGame(firstUser, secondGame, liked = true)
        saveUserGame(secondUser, secondGame, liked = true)
        saveUserGame(thirdUser, thirdGame, liked = true)
        saveUserGame(thirdUser, unlikedGame, liked = false)

        // when: 인기 게임 목록을 조회한다.
        val result = gameStatisticsRepository.findPopularGames(PageRequest.of(0, 10))

        // then: 좋아요 수 내림차순과 게임 ID 내림차순으로 정렬되고 좋아요 없는 게임은 제외한다.
        assertThat(result.map { it.gameId }).containsExactly(secondGame.id, firstGame.id, thirdGame.id)
        assertThat(result.map { it.likeCount }).containsExactly(2L, 2L, 1L)
        assertThat(result.first().title).isEqualTo("두 번째 게임")
        assertThat(result.first().coverImageUrl).isEqualTo("https://images.igdb.com/1002.jpg")
    }

    @Test
    @DisplayName("요청한 개수만큼 인기 게임을 조회한다")
    fun findPopularGamesAppliesSizeLimit() {
        // given: 좋아요가 있는 게임 두 개를 준비한다.
        val firstGame = saveGame(2001L, "첫 번째 게임")
        val secondGame = saveGame(2002L, "두 번째 게임")
        val user = saveUser(10)
        saveUserGame(user, firstGame, liked = true)
        saveUserGame(user, secondGame, liked = true)

        // when: 조회 개수를 1개로 제한해 인기 게임을 조회한다.
        val result = gameStatisticsRepository.findPopularGames(PageRequest.of(0, 1))

        // then: 게임 ID가 더 큰 게임 한 개만 반환한다.
        assertThat(result.map { it.gameId }).containsExactly(secondGame.id)
    }

    private fun saveGame(igdbId: Long, title: String): Game {
        val response = IgdbGameResponse(
            id = igdbId,
            name = title,
            summary = "테스트 게임 설명",
            cover = IgdbGameResponse.Cover(id = igdbId, url = "//images.igdb.com/$igdbId.jpg"),
            first_release_date = 1704067200L,
            rating = BigDecimal("90.50"),
            involved_companies = mutableListOf(),
            genres = mutableListOf(),
            platforms = mutableListOf(),
            collections = mutableListOf(),
        )
        return gameRepository.save(Game.createFromIgdb(response))
    }

    private fun saveUser(number: Int): User =
        userRepository.save(User("인기게임사용자$number", "popular-game-$number@test.com", "password"))

    private fun saveUserGame(user: User, game: Game, liked: Boolean) {
        val userGame = UserGame(user, game)
        userGame.changeLiked(liked)
        userGameRepository.save(userGame)
    }
}
