package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameListResponse
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest

@ExtendWith(MockitoExtension::class)
class UserGameServiceRecentTest {
    @Mock
    private lateinit var userRepository: UserRepository
    @Mock
    private lateinit var gameRepository: GameRepository
    @Mock
    private lateinit var platformRepository: PlatformRepository
    @Mock
    private lateinit var userFavoriteGameRepository: UserFavoriteGameRepository

    @Mock
    private lateinit var changeRecorder: com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder

    @InjectMocks
    private lateinit var userGameService: UserGameService

    @Mock
    private lateinit var userGameRepository: UserGameRepository

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    @Test
    @DisplayName("최근 플레이한 게임을 최대 5개 요청하고 조회 결과를 반환한다")
    fun getRecentPlayedGames() {
        val userId = 1L
        val pageable = PageRequest.of(0, 5)
        val userGame1 = createUserGame(1L, "Game 1", "cover1")
        val userGame2 = createUserGame(2L, "Game 2", "cover2")
        given(userGameRepository.findRecentPlayedGames(userId, pageable))
            .willReturn(listOf(userGame1, userGame2))

        val result = userGameService.getRecentPlayedGames(userId)

        assertThat(result).containsExactly(
            UserGameListResponse(
                gameId = 1L,
                title = "Game 1",
                coverImageUrl = "cover1",
                playStatus = null,
                playing = false,
                backlog = false,
                wishlist = false,
                liked = false,
            ),
            UserGameListResponse(
                gameId = 2L,
                title = "Game 2",
                coverImageUrl = "cover2",
                playStatus = null,
                playing = false,
                backlog = false,
                wishlist = false,
                liked = false,
            ),
        )
        verify(userGameRepository).findRecentPlayedGames(userId, pageable)
    }

    private fun createUserGame(gameId: Long, title: String, cover: String): UserGame {
        val game = mock(Game::class.java)
        given(game.id).willReturn(gameId)
        given(game.title).willReturn(title)
        given(game.coverImageUrl).willReturn(cover)

        return mock(UserGame::class.java).apply {
            given(this.game).willReturn(game)
        }
    }

}
