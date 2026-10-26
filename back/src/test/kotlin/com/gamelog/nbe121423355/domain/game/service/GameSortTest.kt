package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import java.math.BigDecimal
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class GameSortTest : GameQueryTestSupport() {

    @Test
    @DisplayName("사용자 평균과 라이브러리 등록 수를 집계한 뒤 페이징한다")
    fun sortsByCommunityStatistics() {
        record("Zelda Both", "one", "5.0", "2", true)
        record("Zelda Both", "two", "1.0", "4", true)
        record("Zelda RPG", "three", "4.0", "20", true)
        record("Zelda RPG", "four", null, null, false)
        record("Zelda RPG", "five", null, null, false)
        // 평균은 Both=3, RPG=4. 라이브러리는 Both=2, RPG=1.
        for (sort in listOf("RATING", "PLAY_TIME", "LIBRARY")) {
            val first = if (sort == "LIBRARY") "Zelda Both" else "Zelda RPG"
            val second = if (sort == "LIBRARY") "Zelda RPG" else "Zelda Both"
            mockMvc
                .perform(
                    get("/api/v1/games/page")
                        .param("sort", sort)
                        .param("keyword", "zelda")
                        .param("genreIds", requireNotNull(rpg.id).toString())
                        .param("platformIds", requireNotNull(console.id).toString())
                        .param("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value(first))
                .andExpect(jsonPath("$.data.totalElements").value(2))
            mockMvc
                .perform(
                    get("/api/v1/games/page")
                        .param("sort", sort)
                        .param("keyword", "zelda")
                        .param("size", "1")
                        .param("page", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].title").value(second))
        }
    }

    @Test
    @DisplayName("사용자 기록이 없으면 모든 집계 정렬은 ID순으로 게임을 반환한다")
    fun communitySortWithoutRecords() {
        for (sort in listOf("RATING", "LIBRARY", "PLAY_TIME")) {
            mockMvc
                .perform(get("/api/v1/games/page").param("sort", sort))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].title").value("Zelda Both"))
                .andExpect(jsonPath("$.data.content[3].title").value("Unclassified"))
        }
    }

    @Test
    @DisplayName("평점 정렬은 삭제와 숨김 리뷰를 제외하고 복원하면 다시 반영한다")
    fun ratingSortExcludesInactiveReviews() {
        record("Zelda Both", "active", "5.0", null, true)
        val deleted = record("Zelda Both", "deleted", "1.0", null, true)
        val hidden = record("Zelda Both", "hidden", "1.0", null, true)
        record("Zelda RPG", "other", "4.0", null, true)
        deleted.deleteByUser()
        hidden.hideByAdmin()
        assertFirstRatedGame("Zelda Both")
        deleted.restore(BigDecimal("1.0"), "restored", false)
        assertFirstRatedGame("Zelda RPG")
    }

    @Test
    @DisplayName("상태가 null인 기존 리뷰는 포함하고 모든 평점이 삭제되면 0으로 정렬한다")
    fun ratingSortSupportsLegacyAndDeletedOnlyReviews() {
        val legacy = record("Zelda RPG", "legacy", "5.0", null, true)
        record("Zelda Both", "active", "4.0", null, true)
        entityManager
            .createQuery("update Review r set r.status = null where r.id = :id")
            .setParameter("id", requireNotNull(legacy.id))
            .executeUpdate()
        entityManager.refresh(legacy)
        assertFirstRatedGame("Zelda RPG")
        legacy.deleteByUser()
        assertFirstRatedGame("Zelda Both")
        mockMvc
            .perform(get("/api/v1/games/page").param("sort", "RATING").param("keyword", "zelda"))
            .andExpect(jsonPath("$.data.content[1].title").value("Zelda RPG"))
            .andExpect(jsonPath("$.data.totalElements").value(2))
    }

    private fun assertFirstRatedGame(title: String) {
        entityManager.flush()
        mockMvc
            .perform(
                get("/api/v1/games/page")
                    .param("sort", "RATING")
                    .param("keyword", "zelda")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].title").value(title))
            .andExpect(jsonPath("$.data.totalElements").value(2))
    }

    private fun record(
        title: String,
        name: String,
        rating: String?,
        hours: String?,
        library: Boolean,
    ): Review {
        val game =
            entityManager
                .createQuery("select g from Game g where g.title = :title", Game::class.java)
                .setParameter("title", title)
                .singleResult
        val user = User(name, "$name@sort.test", "test-password")
        entityManager.persist(user)
        val userGame = UserGame(user, game)
        userGame.updatePlayRecord(
            null,
            library,
            false,
            false,
            true,
            null,
            hours?.let(::BigDecimal),
            null,
            null,
            null,
            null,
            null,
        )
        entityManager.persist(userGame)
        val review = Review(userGame, rating?.let(::BigDecimal), "review", false)
        entityManager.persist(review)
        entityManager.flush()
        return review
    }
}
