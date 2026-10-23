package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Pageable

@ExtendWith(MockitoExtension::class)
class UserGameServiceRecentTest {
    @Mock
    private lateinit var userRepository: UserRepository
    @Mock
    private lateinit var gameRepository: GameRepository
    @Mock
    private lateinit var platformRepository: PlatformRepository
    @Mock
    private lateinit var userFollowRepository: UserFollowRepository
    @Mock
    private lateinit var userFavoriteGameRepository: UserFavoriteGameRepository

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

        val userGame1 = mock(UserGame::class.java)
        val userGame2 = mock(UserGame::class.java)

        val game1 = mock(Game::class.java)
        val game2 = mock(Game::class.java)

        given(userGame1.game).willReturn(game1)
        given(userGame2.game).willReturn(game2)

        given(game1.id).willReturn(1L)
        given(game2.id).willReturn(2L)

        given(game1.title).willReturn("Game 1")
        given(game2.title).willReturn("Game 2")

        given(game1.coverImageUrl).willReturn("cover1")
        given(game2.coverImageUrl).willReturn("cover2")

        given(userGameRepository.findRecentPlayedGames(
            eq(userId),
            any(Pageable::class.java) ?: Pageable.unpaged()
        )).willReturn(listOf(userGame1, userGame2))

        val result = userGameService.getRecentPlayedGames(userId)

        assertThat(result).hasSize(2)
        assertThat(result[0].gameId).isEqualTo(1L)
        assertThat(result[1].gameId).isEqualTo(2L)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)

        verify(userGameRepository)
            .findRecentPlayedGames(
                eq(userId),
                captor.capture() ?: Pageable.unpaged()
            )

        val pageable = captor.value

        assertThat(pageable.pageNumber).isZero()
        assertThat(pageable.pageSize).isEqualTo(5)
    }

    @Test
    @DisplayName("최근 작성한 리뷰를 최대 3개 요청하고 조회 결과를 반환한다")
    fun getRecentReviews() {
        val userId = 1L

        val review1 = mock(Review::class.java)
        val review2 = mock(Review::class.java)

        val userGame1 = mock(UserGame::class.java)
        val userGame2 = mock(UserGame::class.java)

        val game1 = mock(Game::class.java)
        val game2 = mock(Game::class.java)

        given(review1.id).willReturn(1L)
        given(review2.id).willReturn(2L)

        val modifiedAt = LocalDateTime.of(2026, 10, 22, 12, 0)
        given(review1.lastModifiedDate).willReturn(modifiedAt)
        given(review2.lastModifiedDate).willReturn(modifiedAt.minusDays(1))

        given(review1.userGame).willReturn(userGame1)
        given(review2.userGame).willReturn(userGame2)

        given(userGame1.game).willReturn(game1)
        given(userGame2.game).willReturn(game2)

        given(game1.id).willReturn(10L)
        given(game2.id).willReturn(20L)

        given(game1.title).willReturn("Game 1")
        given(game2.title).willReturn("Game 2")

        given(game1.coverImageUrl).willReturn("cover1")
        given(game2.coverImageUrl).willReturn("cover2")

        given(reviewRepository.findRecentReviews(
            eq(userId),
            any(Pageable::class.java) ?: Pageable.unpaged()
        )).willReturn(mutableListOf(review1, review2))

        val result = userGameService.getRecentReviews(userId)

        assertThat(result).hasSize(2)
        assertThat(result[0].reviewId).isEqualTo(1L)
        assertThat(result[0].gameId).isEqualTo(10L)
        assertThat(result[1].reviewId).isEqualTo(2L)
        assertThat(result[1].gameId).isEqualTo(20L)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)

        verify(reviewRepository)
            .findRecentReviews(
                eq(userId),
                captor.capture() ?: Pageable.unpaged()
            )

        val pageable = captor.value

        assertThat(pageable.pageNumber).isZero()
        assertThat(pageable.pageSize).isEqualTo(3)
    }
}
