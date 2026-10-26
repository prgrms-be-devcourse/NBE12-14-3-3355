package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GameSuggestionTest : GameQueryTestSupport() {
    @Test
    fun suggestionsKeepIdTieBreakAndExcludeUnknownReleaseDates() {
        saveGame(9400L, "Needle", listOf(), listOf())
        saveGame(9401L, "Needle", listOf(), listOf())
        entityManager.persist(
            Game.createFromIgdb(
                IgdbGameResponse(
                    id = 9402L,
                    name = "Needle Unknown",
                    summary = null,
                    cover = null,
                    first_release_date = null,
                    rating = null,
                    involved_companies = mutableListOf(),
                    genres = mutableListOf(),
                    platforms = mutableListOf(),
                    collections = mutableListOf(),
                )
            )
        )
        saveGame(9403L, "A Needle", listOf(), listOf())
        entityManager.flush()
        entityManager.clear()
        val result = gameService.getSuggestions("needle")
        assertThat(result.map { it.title }).containsExactly("Needle", "Needle", "A Needle")
        assertThat(requireNotNull(result[0].id)).isLessThan(requireNotNull(result[1].id))
    }

    @Test
    @DisplayName("검색 후보는 제한 전에 완전 일치, 시작 일치, 포함 일치 순으로 정렬한다")
    fun ranksSuggestionsBeforeLimiting() {
        for (i in 0 until 8) {
            saveGame(9100L + i, "A$i Thief", listOf(), listOf())
        }
        saveGame(9200L, "Thief II: The Metal Age", listOf(), listOf())
        saveGame(9201L, "Thief", listOf(), listOf())
        mockMvc
            .perform(get("/api/v1/games/suggestions").param("keyword", " tHiEf "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(6))
            .andExpect(jsonPath("$.data[0].title").value("Thief"))
            .andExpect(jsonPath("$.data[1].title").value("Thief II: The Metal Age"))
            .andExpect(jsonPath("$.data[2].title").value("A0 Thief"))
    }

    @Test
    @DisplayName("검색 후보는 LIKE 특수문자를 문자 그대로 검색하며 빈 검색에는 빈 목록을 반환한다")
    fun suggestionsEscapeWildcardsAndHandleEmptyInput() {
        saveGame(9300L, "100%_!", listOf(), listOf())
        saveGame(9301L, "100abc", listOf(), listOf())
        assertThat(gameService.getSuggestions("%_!").map { it.title }).containsExactly("100%_!")
        assertThat(gameService.getSuggestions("no-such-title-1234")).isEmpty()
        assertThat(gameService.getSuggestions("   ")).isEmpty()
        mockMvc
            .perform(get("/api/v1/games/suggestions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0))
    }
}
