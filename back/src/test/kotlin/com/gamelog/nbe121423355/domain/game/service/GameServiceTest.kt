package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameListResponse
import com.gamelog.nbe121423355.domain.game.dto.GameSearchRequest
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class GameServiceTest {

    @Autowired private lateinit var gameService: GameService

    @Autowired private lateinit var gameRepository: GameRepository

    @Test
    @DisplayName("페이지 조회는 저장된 게임의 DTO 필드를 반환한다")
    fun getGamesPageMapsDtoFields() {
        // given
        assertThat(gameRepository.count()).isZero()

        val firstGame = gameRepository.save(createGame(1001L, "첫 번째 게임"))
        val secondGame = gameRepository.save(createGame(1002L, "두 번째 게임"))

        // when
        val responses =
            gameService
                .getGamesPage(
                    GameSearchRequest(
                        page = 0,
                        size = 20,
                        sort = null,
                        keyword = null,
                        genreIds = null,
                        platformIds = null,
                    )
                )
                .content

        // then
        assertThat(responses)
            .containsExactly(
                GameListResponse(
                    id = requireNotNull(firstGame.id),
                    title = "첫 번째 게임",
                    coverImageUrl = "https://images.igdb.com/test.jpg",
                    releaseDate = LocalDate.of(2024, 1, 1),
                    igdbRating = BigDecimal("90.50"),
                ),
                GameListResponse(
                    id = requireNotNull(secondGame.id),
                    title = "두 번째 게임",
                    coverImageUrl = "https://images.igdb.com/test.jpg",
                    releaseDate = LocalDate.of(2024, 1, 1),
                    igdbRating = BigDecimal("90.50"),
                ),
            )
    }

    @Test
    @DisplayName("페이징 조회는 게임 3개를 2개씩 두 페이지로 반환한다")
    fun getGamesPageReturnsPages() {
        // given
        assertThat(gameRepository.count()).isZero()

        gameRepository.save(createGame(1001L, "첫 번째 게임"))
        gameRepository.save(createGame(1002L, "두 번째 게임"))
        gameRepository.save(createGame(1003L, "세 번째 게임"))

        // when
        val firstPage =
            gameService.getGamesPage(
                GameSearchRequest(
                    page = 0,
                    size = 2,
                    sort = null,
                    keyword = null,
                    genreIds = null,
                    platformIds = null,
                )
            )

        val secondPage =
            gameService.getGamesPage(
                GameSearchRequest(
                    page = 1,
                    size = 2,
                    sort = null,
                    keyword = null,
                    genreIds = null,
                    platformIds = null,
                )
            )

        // then
        assertThat(firstPage.content.map { it.title }).containsExactly("첫 번째 게임", "두 번째 게임")

        assertThat(firstPage.number).isZero()
        assertThat(firstPage.size).isEqualTo(2)
        assertThat(firstPage.totalElements).isEqualTo(3)
        assertThat(firstPage.totalPages).isEqualTo(2)
        assertThat(firstPage.isFirst).isTrue()
        assertThat(firstPage.hasNext()).isTrue()

        assertThat(secondPage.content.map { it.title }).containsExactly("세 번째 게임")

        assertThat(secondPage.number).isEqualTo(1)
        assertThat(secondPage.totalElements).isEqualTo(3)
        assertThat(secondPage.totalPages).isEqualTo(2)
        assertThat(secondPage.isLast).isTrue()
        assertThat(secondPage.hasNext()).isFalse()
    }

    @Test
    @DisplayName("페이징 조회 시 저장된 게임이 없으면 빈 페이지를 반환한다")
    fun getGamesPageReturnsEmptyPageWhenNoGamesExist() {
        // given
        assertThat(gameRepository.count()).isZero()

        val request =
            GameSearchRequest(
                page = 0,
                size = 20,
                sort = null,
                keyword = null,
                genreIds = null,
                platformIds = null,
            )

        // when
        val responses = gameService.getGamesPage(request)

        // then
        assertThat(responses.content).isEmpty()
        assertThat(responses.totalElements).isZero()
        assertThat(responses.totalPages).isZero()
        assertThat(responses.hasNext()).isFalse()
    }

    @Test
    @DisplayName("마지막 페이지를 초과하면 목록은 비어 있고 전체 개수는 유지된다")
    fun getGamesPageReturnsEmptyContentWhenPageExceedsLastPage() {
        // given
        assertThat(gameRepository.count()).isZero()

        gameRepository.save(createGame(1001L, "첫 번째 게임"))
        gameRepository.save(createGame(1002L, "두 번째 게임"))
        gameRepository.save(createGame(1003L, "세 번째 게임"))

        // when
        val responses =
            gameService.getGamesPage(
                GameSearchRequest(
                    page = 2,
                    size = 2,
                    sort = null,
                    keyword = null,
                    genreIds = null,
                    platformIds = null,
                )
            )

        // then
        assertThat(responses.content).isEmpty()
        assertThat(responses.number).isEqualTo(2)
        assertThat(responses.totalElements).isEqualTo(3)
        assertThat(responses.totalPages).isEqualTo(2)
        assertThat(responses.hasNext()).isFalse()
    }

    @Test
    @DisplayName("제목 검색은 대소문자와 앞뒤 공백을 무시하고 검색 결과를 페이징한다")
    fun searchGamesReturnsMatchingPage() {
        assertThat(gameRepository.count()).isZero()
        gameRepository.save(createGame(1001L, "The Legend of Zelda"))
        gameRepository.save(createGame(1002L, "ZELDA Adventure"))
        gameRepository.save(createGame(1003L, "Super Mario"))

        val firstPage =
            gameService.getGamesPage(
                GameSearchRequest(
                    page = 0,
                    size = 1,
                    sort = null,
                    keyword = " zelda ",
                    genreIds = null,
                    platformIds = null,
                )
            )
        val secondPage =
            gameService.getGamesPage(
                GameSearchRequest(
                    page = 1,
                    size = 1,
                    sort = null,
                    keyword = " zelda ",
                    genreIds = null,
                    platformIds = null,
                )
            )

        assertThat(firstPage.content.map { it.title }).containsExactly("The Legend of Zelda")
        assertThat(secondPage.content.map { it.title }).containsExactly("ZELDA Adventure")
        assertThat(firstPage.totalElements).isEqualTo(2)
        assertThat(firstPage.totalPages).isEqualTo(2)
        assertThat(secondPage.hasNext()).isFalse()
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 페이지를 반환한다")
    fun searchGamesWithNoMatchReturnsEmptyPage() {
        assertThat(gameRepository.count()).isZero()
        gameRepository.save(createGame(1001L, "Super Mario"))

        val result =
            gameService.getGamesPage(
                GameSearchRequest(
                    page = 0,
                    size = 20,
                    sort = null,
                    keyword = "zelda",
                    genreIds = null,
                    platformIds = null,
                )
            )

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isZero()
        assertThat(result.totalPages).isZero()
    }

    private fun createGame(igdbId: Long, title: String): Game {
        val response =
            IgdbGameResponse(
                id = igdbId,
                name = title,
                summary = "테스트 게임 설명",
                cover =
                    IgdbGameResponse.Cover(
                        id = 1L,
                        url = "//images.igdb.com/test.jpg",
                    ),
                first_release_date = 1704067200L,
                rating = BigDecimal("90.50"),
                involved_companies = mutableListOf(),
                genres = mutableListOf(),
                platforms = mutableListOf(),
                collections = mutableListOf(),
            )

        return Game.createFromIgdb(response)
    }
}
