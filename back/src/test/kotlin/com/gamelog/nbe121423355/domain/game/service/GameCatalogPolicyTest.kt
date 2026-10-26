package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameListResponse
import com.gamelog.nbe121423355.domain.game.dto.GameSearchRequest
import com.gamelog.nbe121423355.domain.game.dto.GameSort
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GameCatalogPolicyTest : GameQueryTestSupport() {
    @Test
    @DisplayName("기본순은 IGDB 평점 내림차순이며 동점은 ID순, 평점 미등록은 마지막이다")
    fun defaultSortUsesIgdbRatingBeforePagination() {
        val date = LocalDate.of(2024, 1, 1)
        save(9101L, "Rated Missing", date, null)
        save(9102L, "Rated Low", date, "70")
        save(9103L, "Rated High First", date, "95")
        save(9104L, "Rated High Second", date, "95")
        save(9105L, "Rated Zero", date, "0")
        entityManager.flush()

        val expected =
            listOf(
                "Rated High First",
                "Rated High Second",
                "Rated Low",
                "Rated Zero",
                "Rated Missing",
            )
        for (page in expected.indices) {
            val result =
                gameService.getGamesPage(
                    GameSearchRequest(
                        page = page,
                        size = 1,
                        sort = null,
                        keyword = "Rated",
                        genreIds = null,
                        platformIds = null,
                    )
                )
            assertThat(result.content.map { it.title }).containsExactly(expected[page])
            assertThat(result.totalElements).isEqualTo(5)
            assertThat(result.totalPages).isEqualTo(5)
        }
    }

    @Test
    @DisplayName("모든 정렬과 자동완성은 1970년 1월 1일부터 오늘까지 출시된 게임만 반환한다")
    fun filtersReleaseDateBoundariesForEverySortAndSuggestions() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        save(9201L, "Boundary Old", LocalDate.of(1969, 12, 31), "100")
        save(9202L, "Boundary Unknown", null, "100")
        save(9203L, "Boundary Future", today.plusDays(1), "100")
        save(9204L, "Boundary Earliest", LocalDate.of(1970, 1, 1), "80")
        save(9205L, "Boundary Current", today, "90")
        entityManager.flush()

        assertThat(gameService.getSuggestions("Boundary").map { it.title })
            .containsExactlyInAnyOrder("Boundary Earliest", "Boundary Current")

        (listOf(null) + GameSort.entries).forEach { sort ->
            val first =
                gameService.getGamesPage(
                    GameSearchRequest(
                        page = 0,
                        size = 1,
                        sort = sort,
                        keyword = "Boundary",
                        genreIds = null,
                        platformIds = null,
                    )
                )
            val second =
                gameService.getGamesPage(
                    GameSearchRequest(
                        page = 1,
                        size = 1,
                        sort = sort,
                        keyword = "Boundary",
                        genreIds = null,
                        platformIds = null,
                    )
                )
            assertThat(first.totalElements).isEqualTo(2)
            assertThat(first.totalPages).isEqualTo(2)
            assertThat(second.totalElements).isEqualTo(2)
            val beyond =
                gameService.getGamesPage(
                    GameSearchRequest(
                        page = 2,
                        size = 1,
                        sort = sort,
                        keyword = "Boundary",
                        genreIds = null,
                        platformIds = null,
                    )
                )
            assertThat(beyond.content).isEmpty()
            assertThat(beyond.totalElements).isEqualTo(2)
            assertThat((first.content + second.content).map(GameListResponse::title))
                .containsExactlyInAnyOrder("Boundary Earliest", "Boundary Current")
        }
    }

    private fun save(id: Long, title: String, date: LocalDate?, rating: String?) {
        entityManager.persist(
            Game.createFromIgdb(
                IgdbGameResponse(
                    id = id,
                    name = title,
                    summary = null,
                    cover = null,
                    first_release_date = date?.atStartOfDay()?.toEpochSecond(ZoneOffset.UTC),
                    rating = rating?.let(::BigDecimal),
                    involved_companies = mutableListOf(),
                    genres = mutableListOf(),
                    platforms = mutableListOf(),
                    collections = mutableListOf(),
                )
            )
        )
    }
}
