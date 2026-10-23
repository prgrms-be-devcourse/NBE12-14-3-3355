package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserFavoriteGameResponse
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyList
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class UserGameServiceFavoriteTest {
    @Mock
    private lateinit var gameRepository: GameRepository
    @Mock
    private lateinit var platformRepository: PlatformRepository
    @Mock
    private lateinit var reviewRepository: ReviewRepository
    @Mock
    private lateinit var userFollowRepository: UserFollowRepository

    @Mock
    private lateinit var changeRecorder: com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder

    @InjectMocks
    private lateinit var userGameService: UserGameService

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userGameRepository: UserGameRepository

    @Mock
    private lateinit var userFavoriteGameRepository: UserFavoriteGameRepository

    @Test
    @DisplayName("인생게임을 요청한 순서대로 저장하고 표시 순서와 함께 반환한다")
    fun updateFavoriteGames() {
        val userId = 1L
        val gameIds = listOf(20L, 10L)
        val user = mock(User::class.java)
        val game1 = mock(Game::class.java)
        val game2 = mock(Game::class.java)
        val userGame1 = mock(UserGame::class.java)
        val userGame2 = mock(UserGame::class.java)
        var savedGames = emptyList<UserFavoriteGame>()

        given(userRepository.findById(userId)).willReturn(Optional.of(user))
        given(user.id).willReturn(userId)
        given(game1.id).willReturn(10L)
        given(game2.id).willReturn(20L)
        given(game1.title).willReturn("Game 1")
        given(game2.title).willReturn("Game 2")
        given(userGame1.game).willReturn(game1)
        given(userGame2.game).willReturn(game2)
        given(userGameRepository.findAllByUser_IdAndGame_IdInAndInLibraryTrue(userId, gameIds))
            .willReturn(listOf(userGame1, userGame2))
        given(userFavoriteGameRepository.saveAll(anyList<UserFavoriteGame>()))
            .willAnswer { invocation ->
                savedGames = invocation.getArgument<List<UserFavoriteGame>>(0)
                savedGames
            }

        val result = userGameService.updateFavoriteGames(userId, gameIds)

        verify(userFavoriteGameRepository).deleteAllByUserId(userId)
        assertThat(savedGames.map { it.game.id }).containsExactly(20L, 10L)
        assertThat(savedGames.map { it.displayOrder }).containsExactly(1, 2)
        assertThat(result).containsExactly(
            UserFavoriteGameResponse(20L, "Game 2", null, 1),
            UserFavoriteGameResponse(10L, "Game 1", null, 2),
        )
    }

    @Test
    @DisplayName("인생게임은 최대 5개까지 등록할 수 있다")
    fun rejectMoreThanFiveFavoriteGames() {
        val gameIds: List<Long> = listOf(1L, 2L, 3L, 4L, 5L, 6L)

        assertThatThrownBy {
            userGameService.updateFavoriteGames(1L, gameIds)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("인생게임에는 같은 게임을 중복 등록할 수 없다")
    fun rejectDuplicateFavoriteGames() {
        val gameIds: List<Long> = listOf(10L, 20L, 10L)

        assertThatThrownBy {
            userGameService.updateFavoriteGames(1L, gameIds)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("인생게임 목록에 빈 리스트를 전달하면 기존 인생게임을 모두 삭제한다")
    fun clearFavoriteGames() {
        val userId = 1L

        val user = mock(User::class.java)

        given(userRepository.findById(userId))
            .willReturn(Optional.of(user))

        val result: List<UserFavoriteGameResponse> = userGameService.updateFavoriteGames(
            userId,
            listOf()
        )

        verify(userFavoriteGameRepository)
            .deleteAllByUserId(userId)

        verify(userFavoriteGameRepository)
            .saveAll(anyList())

        assertThat(result).isEmpty()
    }
}
