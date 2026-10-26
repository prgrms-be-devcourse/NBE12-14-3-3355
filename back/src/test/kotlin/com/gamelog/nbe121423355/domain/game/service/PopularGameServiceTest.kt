package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.repository.GameGenreRepository
import com.gamelog.nbe121423355.domain.game.repository.GameStatisticsRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.PopularGameProjection
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyList
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest

@ExtendWith(MockitoExtension::class)
class PopularGameServiceTest {
    @Mock
    private lateinit var gameStatisticsRepository: GameStatisticsRepository

    @Mock
    private lateinit var gameGenreRepository: GameGenreRepository

    @InjectMocks
    private lateinit var popularGameService: PopularGameService

    @Test
    @DisplayName("인기 게임 기본 정보와 장르를 함께 반환한다")
    fun getPopularGamesReturnsGamesWithGenres() {
        // given: 인기 게임 한 개와 해당 게임의 장르 두 개를 준비한다.
        val popularGame = mock(PopularGameProjection::class.java)
        val game = mock(Game::class.java)
        val action = mock(Genre::class.java)
        val rpg = mock(Genre::class.java)
        val actionGenre = mock(GameGenre::class.java)
        val rpgGenre = mock(GameGenre::class.java)
        `when`(popularGame.gameId).thenReturn(1L)
        `when`(popularGame.title).thenReturn("인기 게임")
        `when`(popularGame.coverImageUrl).thenReturn("https://image.test/game.jpg")
        `when`(popularGame.likeCount).thenReturn(10L)
        `when`(game.id).thenReturn(1L)
        `when`(action.id).thenReturn(11L)
        `when`(action.name).thenReturn("Action")
        `when`(rpg.id).thenReturn(12L)
        `when`(rpg.name).thenReturn("RPG")
        `when`(actionGenre.game).thenReturn(game)
        `when`(actionGenre.genre).thenReturn(action)
        `when`(rpgGenre.game).thenReturn(game)
        `when`(rpgGenre.genre).thenReturn(rpg)
        `when`(gameStatisticsRepository.findPopularGames(PageRequest.of(0, 5)))
            .thenReturn(listOf(popularGame))
        `when`(gameGenreRepository.findAllWithGenreByGameIdIn(listOf(1L)))
            .thenReturn(listOf(actionGenre, rpgGenre))

        // when: 기본 크기로 인기 게임을 조회한다.
        val result = popularGameService.getPopularGames(5)

        // then: 게임 정보와 모든 장르가 하나의 응답으로 조립된다.
        assertThat(result).hasSize(1)
        val response = result.single()
        assertThat(response.gameId).isEqualTo(1L)
        assertThat(response.title).isEqualTo("인기 게임")
        assertThat(response.coverImageUrl).isEqualTo("https://image.test/game.jpg")
        assertThat(response.likeCount).isEqualTo(10L)
        assertThat(response.genres.map { it.name }).containsExactly("Action", "RPG")
    }

    @Test
    @DisplayName("장르와 커버 이미지가 없는 게임도 조회 순서대로 반환한다")
    fun getPopularGamesPreservesOrderWithoutGenresOrCoverImage() {
        // given: 조회 순서가 ID 오름차순과 다른 두 게임과 빈 장르 목록을 준비한다.
        val firstGame = mock(PopularGameProjection::class.java)
        val secondGame = mock(PopularGameProjection::class.java)
        `when`(firstGame.gameId).thenReturn(2L)
        `when`(firstGame.title).thenReturn("첫 번째 인기 게임")
        `when`(secondGame.gameId).thenReturn(1L)
        `when`(secondGame.title).thenReturn("두 번째 인기 게임")
        `when`(gameStatisticsRepository.findPopularGames(PageRequest.of(0, 5)))
            .thenReturn(listOf(firstGame, secondGame))
        `when`(gameGenreRepository.findAllWithGenreByGameIdIn(listOf(2L, 1L)))
            .thenReturn(emptyList())

        // when: 장르와 커버 이미지가 없는 인기 게임을 조회한다.
        val result = popularGameService.getPopularGames(5)

        // then: 기존 조회 순서와 null 커버 이미지를 유지하고 장르는 빈 목록으로 반환한다.
        assertThat(result.map { it.gameId }).containsExactly(2L, 1L)
        assertThat(result).allSatisfy { game ->
            assertThat(game.coverImageUrl).isNull()
            assertThat(game.genres).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 50])
    @DisplayName("조회 개수의 최솟값과 최댓값을 허용한다")
    fun getPopularGamesAcceptsBoundarySizes(size: Int) {
        // given: 허용 범위 경계의 조회 개수와 빈 조회 결과를 준비한다.
        `when`(gameStatisticsRepository.findPopularGames(PageRequest.of(0, size))).thenReturn(emptyList())

        // when: 최솟값 또는 최댓값으로 인기 게임을 조회한다.
        val result = popularGameService.getPopularGames(size)

        // then: 예외 없이 빈 목록을 반환하고 장르 조회를 생략한다.
        assertThat(result).isEmpty()
        verifyNoInteractions(gameGenreRepository)
    }

    @Test
    @DisplayName("인기 게임이 없으면 장르를 조회하지 않고 빈 목록을 반환한다")
    fun getPopularGamesReturnsEmptyListWithoutGenreQuery() {
        // given: 인기 게임 조회 결과가 비어 있도록 준비한다.
        `when`(gameStatisticsRepository.findPopularGames(PageRequest.of(0, 5))).thenReturn(emptyList())

        // when: 인기 게임을 조회한다.
        val result = popularGameService.getPopularGames(5)

        // then: 빈 목록을 반환하고 장르 Repository는 호출하지 않는다.
        assertThat(result).isEmpty()
        verify(gameGenreRepository, never()).findAllWithGenreByGameIdIn(anyList())
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 51])
    @DisplayName("조회 개수가 허용 범위를 벗어나면 예외가 발생한다")
    fun getPopularGamesRejectsInvalidSize(size: Int) {
        // given: 허용 범위를 벗어난 조회 개수를 준비한다.
        val invalidSize = size

        // when: 잘못된 조회 개수로 인기 게임 조회를 요청한다.
        val failure = catchThrowable { popularGameService.getPopularGames(invalidSize) }

        // then: 400 예외가 발생하고 Repository는 호출하지 않는다.
        assertThat(failure).isInstanceOfSatisfying(ServiceException::class.java) {
            assertThat(it.resultCode).isEqualTo("400-9")
        }
        verifyNoInteractions(gameStatisticsRepository, gameGenreRepository)
    }
}
