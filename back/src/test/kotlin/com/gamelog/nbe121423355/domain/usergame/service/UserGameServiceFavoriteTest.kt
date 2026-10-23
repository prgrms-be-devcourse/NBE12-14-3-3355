package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserFavoriteGameResponse
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
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

    @InjectMocks
    private lateinit var userGameService: UserGameService

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userGameRepository: UserGameRepository

    @Mock
    private lateinit var userFavoriteGameRepository: UserFavoriteGameRepository

    @Test
    @DisplayName("인생게임을 등록할 수 있다")
    fun updateFavoriteGames() {
        val userId = 1L

        val user = mock(User::class.java)
        val game1 = mock(Game::class.java)
        val game2 = mock(Game::class.java)

        val userGame1 = mock(UserGame::class.java)
        val userGame2 = mock(UserGame::class.java)

        given(userRepository.findById(userId))
            .willReturn(Optional.of(user))

        given(game1.id).willReturn(10L)
        given(game2.id).willReturn(20L)
        given(game1.title).willReturn("Game 1")
        given(game2.title).willReturn("Game 2")

        given(userGame1.game).willReturn(game1)
        given(userGame2.game).willReturn(game2)

        given(userGameRepository.findAllByUser_IdAndGame_IdInAndInLibraryTrue(
            userId,
            listOf(10L, 20L)
        )).willReturn(listOf(userGame1, userGame2))

        val result: List<UserFavoriteGameResponse> = userGameService.updateFavoriteGames(
            userId,
            listOf(10L, 20L)
        )

        verify(userFavoriteGameRepository)
            .deleteAllByUserId(userId)

        verify(userFavoriteGameRepository)
            .saveAll(anyList())

        assertThat(result).hasSize(2)
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

    @Test
    @DisplayName("라이브러리에 등록하지 않은 게임은 인생게임으로 등록할 수 없다")
    fun rejectGameNotInLibrary() {
        val userId = 1L

        val user = mock(User::class.java)

        given(userRepository.findById(userId))
            .willReturn(Optional.of(user))

        given(userGameRepository.findAllByUser_IdAndGame_IdInAndInLibraryTrue(
            userId,
            listOf(10L, 20L)
        )).willReturn(listOf())

        assertThatThrownBy {
            userGameService.updateFavoriteGames(
                userId,
                listOf(10L, 20L)
            )
        }
            .isInstanceOfSatisfying(ServiceException::class.java) { error ->
            assertThat(error.resultCode).isEqualTo("400-11")
            assertThat(error.msg).isEqualTo("라이브러리에 없는 게임이 포함되어 있습니다. 선택 목록을 확인해 주세요.")
        }

        verify(userFavoriteGameRepository, never())
            .deleteAllByUserId(userId)

        verify(userFavoriteGameRepository, never())
            .saveAll(anyList())
    }
}
