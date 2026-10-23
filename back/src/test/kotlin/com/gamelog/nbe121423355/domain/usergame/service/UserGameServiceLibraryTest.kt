package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Platform
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class UserGameServiceLibraryTest {
    private val matcherUserGame = createUserGame()

    @Mock
    private lateinit var userFollowRepository: UserFollowRepository

    @Mock
    private lateinit var userFavoriteGameRepository: UserFavoriteGameRepository

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    @Mock
    private lateinit var changeRecorder: com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder

    @InjectMocks
    private lateinit var userGameService: UserGameService

    @Mock
    private lateinit var userGameRepository: UserGameRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var gameRepository: GameRepository

    @Mock
    private lateinit var platformRepository: PlatformRepository

    @Test
    @DisplayName("라이브러리에 게임을 등록할 수 있다")
    fun addGameToLibrary() {
        val userId = 1L
        val gameId = 1L
        val platformId = 1L

        val user = User("테스트", "test@test.com", "password")
        val game = mock(Game::class.java)
        val platform = mock(Platform::class.java)

        val request = createRequest(
            playStatus = PlayStatus.PLAYED,
            isLiked = true,
            platformId = platformId,
            playTimeHours = BigDecimal("42.5"),
            finishTimeHours = BigDecimal("35.0"),
            masterTimeHours = BigDecimal("80.0"),
            startedAt = LocalDate.of(2026, 8, 1),
            completedAt = LocalDate.of(2026, 8, 20),
            lastPlayedAt = LocalDateTime.of(2026, 8, 20, 21, 30),
        )

        given(platformRepository.findById(platformId))
            .willReturn(Optional.of(platform))
        stubNewUserGame(userId, gameId, user, game)

        val result = userGameService.addOrUpdateGameToLibrary(
            userId,
            gameId,
            request
        )

        assertThat(result.created).isTrue()
        assertThat(result.userGame.user).isEqualTo(user)
        assertThat(result.userGame.game).isEqualTo(game)

        verify(userGameRepository).save(anyUserGame())
    }

    @Test
    @DisplayName("이미 등록된 게임이면 기존 UserGame을 수정한다")
    fun updateExistingGame() {
        val userId = 1L
        val gameId = 1L
        val platformId = 1L

        val user = User("테스트", "test@test.com", "password")
        val game = mock(Game::class.java)
        val platform = mock(Platform::class.java)

        val userGame = UserGame(user, game)

        val request = createRequest(
            playStatus = PlayStatus.PLAYED,
            isPlaying = true,
            isLiked = true,
            platformId = platformId,
            playTimeHours = BigDecimal("42.5"),
            finishTimeHours = BigDecimal("35.0"),
            masterTimeHours = BigDecimal("80.0"),
            startedAt = LocalDate.of(2026, 8, 1),
            completedAt = LocalDate.of(2026, 8, 20),
            lastPlayedAt = LocalDateTime.of(2026, 8, 20, 21, 30),
        )

        given(platformRepository.findById(platformId))
            .willReturn(Optional.of(platform))
        given(userGameRepository.findByUser_IdAndGame_Id(userId, gameId))
            .willReturn(userGame)

        val result = userGameService.addOrUpdateGameToLibrary(
            userId,
            gameId,
            request
        )

        assertThat(result.created).isFalse()
        assertThat(result.userGame).isEqualTo(userGame)
        assertThat(userGame.playStatus).isEqualTo(PlayStatus.PLAYED)
        assertThat(userGame.playing).isTrue()
        assertThat(userGame.liked).isTrue()

        verify(userGameRepository, never()).save(anyUserGame())
    }

    @Test
    @DisplayName("완료일이 시작일보다 빠르면 저장할 수 없다")
    fun rejectCompletedDateBeforeStartedDate() {
        val startedAt = LocalDate.now().minusDays(1)
        val completedAt = startedAt.minusDays(1)

        val request = createRequest(
            PlayStatus.COMPLETED,
            startedAt,
            completedAt,
            null
        )

        assertThatThrownBy {
            userGameService.addOrUpdateGameToLibrary(
                1L,
                1L,
                request
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("완료일은 시작일보다 빠를 수 없습니다.")

        verifyNoInteractions(platformRepository, userGameRepository)
    }

    @Test
    @DisplayName("마지막 플레이 날짜가 미래면 저장할 수 없다")
    fun rejectFutureLastPlayedDate() {
        val lastPlayedAt = LocalDate.now().plusDays(1).atStartOfDay()

        val request = createRequest(
            PlayStatus.PLAYED,
            null,
            null,
            lastPlayedAt
        )

        assertThatThrownBy {
            userGameService.addOrUpdateGameToLibrary(
                1L,
                1L,
                request
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("마지막 플레이 날짜는 오늘보다 미래일 수 없습니다.")

        verifyNoInteractions(platformRepository, userGameRepository)
    }

    @Test
    @DisplayName("완료일이 미래면 저장할 수 없다")
    fun rejectFutureCompletedDate() {
        val request = createRequest(
            PlayStatus.COMPLETED,
            LocalDate.now(),
            LocalDate.now().plusDays(1),
            null
        )

        assertThatThrownBy {
            userGameService.addOrUpdateGameToLibrary(
                1L,
                1L,
                request
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("완료일은 오늘보다 미래일 수 없습니다.")

        verifyNoInteractions(platformRepository, userGameRepository)
    }

    @Test
    @DisplayName("시작일이 미래면 저장할 수 없다")
    fun rejectFutureStartedDate() {
        val request = createRequest(
            PlayStatus.PLAYED,
            LocalDate.now().plusDays(1),
            null,
            null
        )

        assertThatThrownBy {
            userGameService.addOrUpdateGameToLibrary(
                1L,
                1L,
                request
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("시작일은 오늘보다 미래일 수 없습니다.")

        verifyNoInteractions(platformRepository, userGameRepository)
    }

    @Test
    @DisplayName("마지막 플레이 날짜가 시작일보다 빠르면 저장할 수 없다")
    fun rejectLastPlayedDateBeforeStartedDate() {
        val startedAt = LocalDate.now().minusDays(1)

        val request = createRequest(
            PlayStatus.PLAYED,
            startedAt,
            null,
            startedAt.minusDays(1).atStartOfDay()
        )

        assertThatThrownBy {
            userGameService.addOrUpdateGameToLibrary(
                1L,
                1L,
                request
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("마지막 플레이 날짜는 시작일보다 빠를 수 없습니다.")

        verifyNoInteractions(platformRepository, userGameRepository)
    }

    @Test
    @DisplayName("UserGame이 없으면 생성하고 플레이 상태를 변경한다")
    fun createUserGameAndChangePlayedStatus() {
        val user = User("테스트", "test@test.com", "password")
        val game = mock(Game::class.java)

        stubNewUserGame(1L, 1L, user, game)

        val result = userGameService.changePlayed(
            1L,
            1L,
            PlayStatus.COMPLETED
        )

        assertThat(result).isEqualTo(PlayStatus.COMPLETED)

        val captor = ArgumentCaptor.forClass(UserGame::class.java)

        verify(userGameRepository).save(captor.capture() ?: matcherUserGame)

        val saved = captor.value

        assertThat(saved.user).isEqualTo(user)
        assertThat(saved.game).isEqualTo(game)
        assertThat(saved.playStatus)
            .isEqualTo(PlayStatus.COMPLETED)
    }

    @Test
    @DisplayName("UserGame이 없을 때 playing을 false로 변경하면 예외가 발생한다")
    fun changePlayingFalseWithoutUserGame() {
        given(userGameRepository.findByUser_IdAndGame_Id(1L, 1L))
            .willReturn(null)

        assertThatThrownBy {
            userGameService.changePlaying(1L, 1L, false)
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("라이브러리에 등록되지 않은 게임입니다.")

        verify(userGameRepository, never())
            .save(anyUserGame())
        verify(userRepository, never()).findById(anyLong())
        verify(gameRepository, never()).findById(anyLong())
    }

    @Test
    @DisplayName("같은 playing 상태를 반복해서 변경해도 상태가 유지된다")
    fun repeatPlayingChange() {
        val userGame = createUserGame()

        given(userGameRepository.findByUser_IdAndGame_Id(1L, 1L))
            .willReturn(userGame)

        assertThat(
            userGameService.changePlaying(1L, 1L, true)
        ).isTrue()

        assertThat(
            userGameService.changePlaying(1L, 1L, true)
        ).isTrue()

        assertThat(userGame.playing).isTrue()
    }

    @Test
    @DisplayName("UserGame이 없을 때 wishlist를 false로 변경하면 예외가 발생한다")
    fun changeWishlistFalseWithoutUserGame() {
        given(userGameRepository.findByUser_IdAndGame_Id(1L, 1L))
            .willReturn(null)

        assertThatThrownBy {
            userGameService.changeWishlist(1L, 1L, false)
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("라이브러리에 등록되지 않은 게임입니다.")

        verify(userGameRepository, never())
            .save(anyUserGame())
    }

    @Test
    @DisplayName("UserGame이 없을 때 backlog를 false로 변경하면 예외가 발생한다")
    fun changeBacklogFalseWithoutUserGame() {
        given(userGameRepository.findByUser_IdAndGame_Id(1L, 1L))
            .willReturn(null)

        assertThatThrownBy {
            userGameService.changeBacklog(1L, 1L, false)
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("라이브러리에 등록되지 않은 게임입니다.")

        verify(userGameRepository, never())
            .save(anyUserGame())
    }

    @Test
    @DisplayName("UserGame이 없을 때 liked를 false로 변경하면 예외가 발생한다")
    fun changeLikedFalseWithoutUserGame() {
        given(userGameRepository.findByUser_IdAndGame_Id(1L, 1L))
            .willReturn(null)

        assertThatThrownBy {
            userGameService.changeLiked(1L, 1L, false)
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("라이브러리에 등록되지 않은 게임입니다.")

        verify(userGameRepository, never())
            .save(anyUserGame())
    }

    @Test
    @DisplayName("UserGame이 없을 때 playStatus를 null로 변경하면 예외가 발생한다")
    fun clearPlayStatusWithoutUserGame() {
        given(userGameRepository.findByUser_IdAndGame_Id(1L, 1L))
            .willReturn(null)

        assertThatThrownBy {
            userGameService.changePlayed(1L, 1L, null)
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("라이브러리에 등록되지 않은 게임입니다.")

        verify(userGameRepository, never())
            .save(anyUserGame())
    }

    @Test
    @DisplayName("liked 상태를 true에서 false로 변경할 수 있다")
    fun changeLikedTrueToFalse() {
        val userGame = createUserGame()
        userGame.changeLiked(true)

        given(userGameRepository.findByUser_IdAndGame_Id(1L, 1L))
            .willReturn(userGame)

        val result = userGameService.changeLiked(1L, 1L, false)

        assertThat(result).isFalse()
        assertThat(userGame.liked).isFalse()
    }

    @ParameterizedTest
    @EnumSource(PlayStatus::class)
    @DisplayName("모든 플레이 상태로 변경할 수 있다")
    fun changeAllPlayStatuses(playStatus: PlayStatus) {
        val userGame = createUserGame()

        given(userGameRepository.findByUser_IdAndGame_Id(1L, 1L))
            .willReturn(userGame)

        val result = userGameService.changePlayed(
            1L,
            1L,
            playStatus
        )

        assertThat(result).isEqualTo(playStatus)
        assertThat(userGame.playStatus)
            .isEqualTo(playStatus)
    }

    private fun stubNewUserGame(userId: Long, gameId: Long, user: User, game: Game) {
        given(userGameRepository.findByUser_IdAndGame_Id(userId, gameId)).willReturn(null)
        given(userRepository.findById(userId)).willReturn(Optional.of(user))
        given(gameRepository.findById(gameId)).willReturn(Optional.of(game))
        given(userGameRepository.save(anyUserGame()))
            .willAnswer { invocation -> invocation.getArgument<UserGame>(0) }
    }

    private fun createUserGame(): UserGame {
        val user = User("테스트", "test@test.com", "password")
        val game = mock(Game::class.java)

        return UserGame(user, game)
    }

    private fun createRequest(
        playStatus: PlayStatus? = null,
        startedAt: LocalDate? = null,
        completedAt: LocalDate? = null,
        lastPlayedAt: LocalDateTime? = null,
        isPlaying: Boolean = false,
        isBacklog: Boolean = false,
        isWishlist: Boolean = false,
        isLiked: Boolean = false,
        platformId: Long? = null,
        playTimeHours: BigDecimal? = null,
        finishTimeHours: BigDecimal? = null,
        masterTimeHours: BigDecimal? = null,
    ) = UserGameReqBody(
        playStatus = playStatus,
        isPlaying = isPlaying,
        isBacklog = isBacklog,
        isWishlist = isWishlist,
        isLiked = isLiked,
        platformId = platformId,
        playTimeHours = playTimeHours,
        finishTimeHours = finishTimeHours,
        masterTimeHours = masterTimeHours,
        startedAt = startedAt,
        completedAt = completedAt,
        lastPlayedAt = lastPlayedAt,
    )

    @Test
    @DisplayName("존재하지 않는 사용자는 라이브러리에 게임을 등록할 수 없다")
    fun addGameWithoutUser() {
        val userId = 999L
        val gameId = 1L

        val request = createRequest(playStatus = PlayStatus.PLAYED)

        given(userGameRepository.findByUser_IdAndGame_Id(userId, gameId))
            .willReturn(null)

        given(userRepository.findById(userId))
            .willReturn(Optional.empty())

        assertThatThrownBy {
            userGameService.addOrUpdateGameToLibrary(
                userId,
                gameId,
                request
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("사용자를 찾을 수 없습니다.")

        verify(gameRepository, never()).findById(anyLong())
        verify(userGameRepository, never()).save(anyUserGame())
    }

    @Test
    @DisplayName("존재하지 않는 게임은 라이브러리에 등록할 수 없다")
    fun addGameWithoutGame() {
        val userId = 1L
        val gameId = 999L

        val user = User(
            "테스트",
            "test@test.com",
            "password"
        )

        val request = createRequest(playStatus = PlayStatus.PLAYED)

        given(userGameRepository.findByUser_IdAndGame_Id(userId, gameId))
            .willReturn(null)

        given(userRepository.findById(userId))
            .willReturn(Optional.of(user))

        given(gameRepository.findById(gameId))
            .willReturn(Optional.empty())

        assertThatThrownBy {
            userGameService.addOrUpdateGameToLibrary(
                userId,
                gameId,
                request
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("게임을 찾을 수 없습니다.")

        verify(userGameRepository, never()).save(anyUserGame())
    }

    @Test
    @DisplayName("존재하지 않는 플랫폼을 지정하면 라이브러리에 게임을 등록할 수 없다")
    fun addGameWithoutPlatform() {
        val userId = 1L
        val gameId = 1L
        val platformId = 999L

        val request = createRequest(
            playStatus = PlayStatus.PLAYED,
            platformId = platformId,
        )

        given(platformRepository.findById(platformId))
            .willReturn(Optional.empty())

        assertThatThrownBy {
            userGameService.addOrUpdateGameToLibrary(
                userId,
                gameId,
                request
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("플랫폼을 찾을 수 없습니다.")

        verify(userGameRepository, never())
            .findByUser_IdAndGame_Id(anyLong(), anyLong())

        verify(userGameRepository, never())
            .save(anyUserGame())
    }

    @Test
    @DisplayName("시작일과 완료일과 마지막 플레이 날짜가 오늘로 같아도 등록할 수 있다")
    fun allowSameDateBoundaries() {
        val userId = 1L
        val gameId = 1L

        val today = LocalDate.now()

        val user = User(
            "테스트",
            "test@test.com",
            "password"
        )

        val game = mock(Game::class.java)

        val request = createRequest(
            PlayStatus.COMPLETED,
            today,
            today,
            today.atStartOfDay()
        )

        stubNewUserGame(userId, gameId, user, game)

        val result = userGameService.addOrUpdateGameToLibrary(
            userId,
            gameId,
            request
        )

        assertThat(result.created).isTrue()

        verify(userGameRepository)
            .save(anyUserGame())
    }
    // Mockito가 매처로 반환하는 null을 미리 만든 값으로 대체해 non-null 계약을 만족한다.
    private fun anyUserGame(): UserGame = any(UserGame::class.java) ?: matcherUserGame
}
