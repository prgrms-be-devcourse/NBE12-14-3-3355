package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameListResponse
import com.gamelog.nbe121423355.domain.game.dto.GameSearchRequest
import com.gamelog.nbe121423355.domain.game.dto.GameSort
import com.gamelog.nbe121423355.domain.game.entity.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GameFilterTest : GameQueryTestSupport() {
    @Test
    fun allSortsPreserveMultipleFiltersAndPageTotals() {
        for (sort in GameSort.entries) {
            val expected =
                if (sort == GameSort.TITLE) listOf("Mario Action", "Zelda Both", "Zelda RPG")
                else listOf("Zelda Both", "Zelda RPG", "Mario Action")
            for (page in 0 until 4) {
                val result =
                    gameService.getGamesPage(
                        GameSearchRequest(
                            page = page,
                            size = 1,
                            sort = sort,
                            keyword = null,
                            genreIds =
                                mutableListOf(requireNotNull(rpg.id), requireNotNull(action.id)),
                            platformIds =
                                mutableListOf(requireNotNull(pc.id), requireNotNull(console.id)),
                        )
                    )
                assertThat(result.totalElements).isEqualTo(3)
                assertThat(result.totalPages).isEqualTo(3)
                assertThat(result.content.map { it.title })
                    .containsExactlyElementsOf(
                        if (page < 3) listOf(expected[page]) else emptyList()
                    )
            }
        }
    }

    @Test
    fun filteredSearchKeepsLikeCharactersLiteralForEverySort() {
        saveGame(9500L, "Literal%_!", listOf(rpg), listOf(pc))
        saveGame(9501L, "LiteralABC", listOf(rpg), listOf(pc))
        entityManager.flush()
        entityManager.clear()
        for (sort in GameSort.entries) {
            val result =
                gameService.getGamesPage(
                    GameSearchRequest(
                        page = 0,
                        size = 1,
                        sort = sort,
                        keyword = "%_!",
                        genreIds = mutableListOf(requireNotNull(rpg.id)),
                        platformIds = mutableListOf(requireNotNull(pc.id)),
                    )
                )
            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content.map { it.title }).containsExactly("Literal%_!")
        }
    }

    @Test
    @DisplayName("검색어 없이 장르만 선택하면 해당 장르의 게임만 반환한다")
    fun filtersByGenreOnly() {
        val result = search(null, mutableListOf(requireNotNull(rpg.id)), null, 0, 20)
        assertThat(result.content.map { it.title }).containsExactly("Zelda Both", "Zelda RPG")
        assertThat(result.totalElements).isEqualTo(2)
    }

    @Test
    @DisplayName("검색어 없이 플랫폼만 선택하면 해당 플랫폼의 게임만 반환한다")
    fun filtersByPlatformOnly() {
        val result = search(null, null, mutableListOf(requireNotNull(pc.id)), 0, 20)
        assertThat(result.content.map { it.title }).containsExactly("Zelda Both", "Mario Action")
    }

    @Test
    @DisplayName("장르와 플랫폼 조건을 모두 만족하는 게임만 반환한다")
    fun combinesGenreAndPlatformWithAnd() {
        val result =
            search(
                null,
                mutableListOf(requireNotNull(rpg.id)),
                mutableListOf(requireNotNull(pc.id)),
                0,
                20,
            )
        assertThat(result.content.map { it.title }).containsExactly("Zelda Both")
    }

    @Test
    @DisplayName("검색어와 두 필터를 함께 적용한다")
    fun combinesKeywordAndFilters() {
        val result =
            search(
                " zELDa ",
                mutableListOf(requireNotNull(action.id)),
                mutableListOf(requireNotNull(pc.id)),
                0,
                20,
            )
        assertThat(result.content.map { it.title }).containsExactly("Zelda Both")
    }

    @Test
    @DisplayName("각 필터의 다중 선택은 OR이며 중복 없이 페이징하고 전체 개수를 센다")
    fun multipleSelectionsDoNotDuplicateGames() {
        val genres = mutableListOf(requireNotNull(rpg.id), requireNotNull(action.id))
        val platforms = mutableListOf(requireNotNull(pc.id), requireNotNull(console.id))
        val first = search(null, genres, platforms, 0, 2)
        val second = search(null, genres, platforms, 1, 2)

        assertThat(first.content.map { it.title }).containsExactly("Zelda Both", "Zelda RPG")
        assertThat(second.content.map { it.title }).containsExactly("Mario Action")
        assertThat(first.totalElements).isEqualTo(3)
        assertThat(first.totalPages).isEqualTo(2)
        assertThat(second.totalElements).isEqualTo(3)
        assertThat(second.hasNext()).isFalse()
    }

    @Test
    @DisplayName("빈 필터 목록은 필터를 적용하지 않아 연결 정보 없는 게임도 반환한다")
    fun emptyFiltersReturnAllGames() {
        val result = search(" ", mutableListOf(), mutableListOf(), 0, 20)
        assertThat(result.content.map { it.title })
            .containsExactly("Zelda Both", "Zelda RPG", "Mario Action", "Unclassified")
    }

    @Test
    @DisplayName("조건에 맞는 게임이나 필터 ID가 없으면 빈 페이지를 반환한다")
    fun unmatchedFiltersReturnEmptyPage() {
        val noMatch =
            search(
                "Mario",
                mutableListOf(requireNotNull(rpg.id)),
                mutableListOf(requireNotNull(console.id)),
                0,
                20,
            )
        assertThat(noMatch.content).isEmpty()
        assertThat(noMatch.totalElements).isZero()
        assertThat(noMatch.totalPages).isZero()
        assertThat(search(null, mutableListOf(Long.MAX_VALUE), null, 0, 20).content).isEmpty()
        assertThat(search(null, null, mutableListOf(Long.MAX_VALUE), 0, 20).content).isEmpty()
    }

    @Test
    @DisplayName("HTTP 요청의 다중 장르와 플랫폼 파라미터가 필터 조회에 전달된다")
    fun bindsMultipleFilterParameters() {
        mockMvc
            .perform(
                get("/api/v1/games/page")
                    .param(
                        "genreIds",
                        requireNotNull(rpg.id).toString(),
                        requireNotNull(action.id).toString(),
                    )
                    .param(
                        "platformIds",
                        requireNotNull(pc.id).toString(),
                        requireNotNull(console.id).toString(),
                    )
                    .param("page", "1")
                    .param("size", "2")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].title").value("Mario Action"))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
    }

    private fun search(
        keyword: String?,
        genres: MutableList<Long>?,
        platforms: MutableList<Long>?,
        page: Int,
        size: Int,
    ): Page<GameListResponse> =
        gameService.getGamesPage(
            GameSearchRequest(
                page = page,
                size = size,
                sort = null,
                keyword = keyword,
                genreIds = genres,
                platformIds = platforms,
            )
        )
}
