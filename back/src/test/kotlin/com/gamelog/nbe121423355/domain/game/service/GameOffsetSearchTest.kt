package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameListResponse
import com.gamelog.nbe121423355.domain.game.dto.GameSearchRequest
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GameOffsetSearchTest : GameQueryTestSupport() {
    @Test
    fun unfilteredCatalogPreservesAllPagesAndTotalWithBlankKeywords() {
        for (i in 0 until 45) {
            entityManager.persist(
                Game.createFromIgdb(
                    IgdbGameResponse(
                        id = 82000L + i,
                        name = "CatalogProbe $i",
                        summary = null,
                        cover = null,
                        first_release_date = 1704067200L,
                        rating = if (i % 2 == 0) BigDecimal("90.00") else null,
                        involved_companies = mutableListOf(),
                        genres = mutableListOf(),
                        platforms = mutableListOf(),
                        collections = mutableListOf(),
                    )
                )
            )
        }
        entityManager.flush()
        entityManager.clear()
        val expected =
            entityManager
                .createQuery(
                    "SELECT g FROM Game g ORDER BY COALESCE(g.igdbRating, -1.0) DESC, g.id",
                    Game::class.java,
                )
                .resultList
                .map(::GameListResponse)
        for (keyword in listOf(null, "", "   ")) {
            val actual = mutableListOf<GameListResponse>()
            for (page in 0 until 5) {
                val result =
                    gameService.getGamesPage(
                        GameSearchRequest(
                            page = page,
                            size = 15,
                            sort = null,
                            keyword = keyword,
                            genreIds = null,
                            platformIds = null,
                        )
                    )
                assertThat(result.totalElements).isEqualTo(expected.size.toLong())
                assertThat(result.sort)
                    .isEqualTo(
                        GameSearchRequest(
                                page = page,
                                size = 15,
                                sort = null,
                                keyword = keyword,
                                genreIds = null,
                                platformIds = null,
                            )
                            .toPageable()
                            .sort
                    )
                actual.addAll(result.content)
            }
            assertThat(actual).containsExactlyElementsOf(expected)
        }
    }

    @Test
    fun pagesPreserveRatingAndIdOrderAndTotalIncludingEmptyPage() {
        val high = mutableListOf<Long>()
        val unrated = mutableListOf<Long>()
        for (i in 0 until 45) {
            val game =
                Game.createFromIgdb(
                    IgdbGameResponse(
                        id = 80000L + i,
                        name = "OffsetProbe $i",
                        summary = null,
                        cover = null,
                        first_release_date = 1704067200L,
                        rating = if (i % 2 == 0) BigDecimal("90.00") else null,
                        involved_companies = mutableListOf(),
                        genres = mutableListOf(),
                        platforms = mutableListOf(),
                        collections = mutableListOf(),
                    )
                )
            entityManager.persist(game)
            (if (i % 2 == 0) high else unrated).add(requireNotNull(game.id))
        }
        entityManager.flush()
        entityManager.clear()
        val expected = high.toMutableList()
        expected.addAll(unrated)
        val actual = mutableListOf<Long>()
        for (page in 0 until 4) {
            val result =
                gameService.getGamesPage(
                    GameSearchRequest(
                        page = page,
                        size = 20,
                        sort = null,
                        keyword = "offsetprobe",
                        genreIds = null,
                        platformIds = null,
                    )
                )
            assertThat(result.totalElements).isEqualTo(45)
            assertThat(result.totalPages).isEqualTo(3)
            assertThat(result.number).isEqualTo(page)
            assertThat(result.numberOfElements)
                .isEqualTo(if (page < 2) 20 else if (page == 2) 5 else 0)
            actual.addAll(result.content.map { requireNotNull(it.id) })
        }
        assertThat(actual).containsExactlyElementsOf(expected)
    }

    @Test
    fun literalWildcardCharactersRemainLiteral() {
        saveGame(81000L, "Literal%_!Needle", listOf(), listOf())
        saveGame(81001L, "LiteralABCNeedle", listOf(), listOf())
        entityManager.flush()
        entityManager.clear()
        val result =
            gameService.getGamesPage(
                GameSearchRequest(
                    page = 0,
                    size = 20,
                    sort = null,
                    keyword = "%_!",
                    genreIds = null,
                    platformIds = null,
                )
            )
        assertThat(result.totalElements).isEqualTo(1)
        assertThat(result.content.map { it.title }).containsExactly("Literal%_!Needle")
    }
}
