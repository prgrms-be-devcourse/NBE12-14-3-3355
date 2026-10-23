package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameListResponse
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameSearchRequest
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameSort
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameTab
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.global.security.SecurityUser
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.Page
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class UserGameSearchTest {
    @Autowired
    private lateinit var em: EntityManager
    @Autowired
    private lateinit var service: UserGameService
    @Autowired
    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private lateinit var other: User
    private lateinit var pc: Platform
    private lateinit var console: Platform
    private lateinit var rpg: Genre
    private lateinit var action: Genre
    private var nextIgdbId = 82000L

    @BeforeEach
    fun setup() {
        owner = User("library", "library@test.com", "password")
        em.persist(owner)
        pc = Platform.createFromIgdb(81001L, "Library PC")
        console = Platform.createFromIgdb(81002L, "Library Console")
        rpg = Genre.createFromIgdb(81001L, "Library RPG")
        action = Genre.createFromIgdb(81002L, "Library Action")
        listOf(pc, console, rpg, action).forEach(em::persist)
        save(title = "가 게임", status = PlayStatus.COMPLETED, platform = pc, hours = 10, day = 1,
            rating = "4.5", genres = listOf(rpg, action))
        save(title = "나 게임", playing = true, platform = console, hours = 20, day = 2,
            rating = "2.0", genres = listOf(action))
        save(title = "다 100%_!", wishlist = true)
        other = User("other", "other@test.com", "password")
        em.persist(other)
        save(user = other, title = "Other", status = PlayStatus.PLAYED, playing = true, backlog = true,
            wishlist = true, platform = pc, hours = 100, day = 3, rating = "5.0", genres = listOf(rpg))
        save(title = "Removed", liked = true)
        em.flush()
        em.clear()
    }

    @Test
    fun defaultsOnlyReturnOwnersLibraryIncludingGamesWithoutPlatform() {
        assertThat(search(UserGameSearchRequest()).content.map { it.title })
            .containsExactly("나 게임", "가 게임", "다 100%_!")
    }

    @Test
    fun filtersEachStatusIndependently() {
        save(title = "Backlog", backlog = true)
        em.flush()
        em.clear()
        for (tab in listOf(UserGameTab.PLAYED, UserGameTab.PLAYING, UserGameTab.BACKLOG, UserGameTab.WISHLIST, UserGameTab.LIKED)) {
            val request = UserGameSearchRequest()
            request.status = tab
            val expected = when (tab) {
                UserGameTab.PLAYED -> "가 게임"
                UserGameTab.PLAYING -> "나 게임"
                UserGameTab.BACKLOG -> "Backlog"
                UserGameTab.WISHLIST -> "다 100%_!"
                UserGameTab.LIKED -> "Removed"
                else -> throw IllegalArgumentException("지원하지 않는 상태: $tab")
            }
            assertThat(search(request).content.map { it.title }).containsExactly(expected)
        }
    }

    @Test
    fun combinesFiltersAndPaginatesWithoutGenreDuplicates() {
        val request = UserGameSearchRequest()
        request.platformIds = listOf(checkNotNull(pc.id), checkNotNull(console.id))
        request.genreIds = listOf(checkNotNull(rpg.id), checkNotNull(action.id))
        request.keyword = " 게임 "
        request.size = 1
        val first = search(request)
        assertThat(first.totalElements).isEqualTo(2L)
        assertThat(first.totalPages).isEqualTo(2)
        assertThat(first.content.map { it.title }).containsExactly("나 게임")
        request.page = 1
        assertThat(search(request).content.map { it.title }).containsExactly("가 게임")
        request.page = 0
        request.platformIds = listOf(checkNotNull(console.id))
        request.genreIds = listOf(checkNotNull(rpg.id))
        assertThat(search(request)).isEmpty()
    }

    @Test
    fun usesRecordedPlatformInsteadOfSupportedPlatforms() {
        val request = UserGameSearchRequest()
        request.platformIds = listOf(checkNotNull(pc.id))
        assertThat(search(request).content.map { it.title }).containsExactly("가 게임")
    }

    @Test
    fun treatsWildcardsAsLiteralText() {
        save(title = "다 100abc_!", wishlist = true)
        save(title = "다 100%X!", wishlist = true)
        em.flush()
        em.clear()
        val request = UserGameSearchRequest().apply { keyword = " %_! " }

        assertThat(search(request).content.map { it.title }).containsExactly("다 100%_!")
    }

    @Test
    fun treatsEmptyFiltersAndBlankKeywordAsAll() {
        val request = UserGameSearchRequest().apply {
            keyword = "  "
            genreIds = emptyList()
            platformIds = emptyList()
        }

        assertThat(search(request).content.map { it.title })
            .containsExactly("나 게임", "가 게임", "다 100%_!")
    }

    @Test
    fun sortsByPersonalRatingWithNullsLast() {
        val request = UserGameSearchRequest().apply { sort = UserGameSort.RATING }

        assertThat(search(request).content.map { it.title })
            .containsExactly("가 게임", "나 게임", "다 100%_!")
    }

    @Test
    fun sortsByTitle() {
        val request = UserGameSearchRequest().apply { sort = UserGameSort.TITLE }

        assertThat(search(request).content.map { it.title })
            .containsExactly("가 게임", "나 게임", "다 100%_!")
    }

    @Test
    fun sortsByPlayTimeWithNullsLast() {
        val request = UserGameSearchRequest().apply { sort = UserGameSort.PLAY_TIME }

        assertThat(search(request).content.map { it.title })
            .containsExactly("나 게임", "가 게임", "다 100%_!")
    }

    @Test
    fun endpointBindsFiltersAndPreservesResponseShape() {
        mockMvc.perform(get("/api/v1/library/games")
            .with(ownerAuthentication())
            .param("status", "PLAYED").param("sort", "RATING")
            .param("genreIds", "${checkNotNull(rpg.id)},${checkNotNull(action.id)}")
            .param("platformIds", checkNotNull(pc.id).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.userGames[0].title").value("가 게임"))
    }

    @Test
    @DisplayName("비로그인 사용자도 다른 사용자의 프로필 통계를 조회할 수 있다")
    fun publicProfileAllowsAnonymousUser() {
        val targetUserId = checkNotNull(other.id)

        mockMvc.perform(get(
            "/api/v1/library/games/profile/{userId}",
            targetUserId
        ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.userId")
                .value(targetUserId))
            .andExpect(jsonPath("$.data.isMe").value(false))
            .andExpect(jsonPath("$.data.isFollowing").value(false))
            .andExpect(jsonPath("$.data.stats.playedGameCount")
                .value(1))
    }

    @Test
    @DisplayName("본인이 사용자 ID 경로로 프로필을 조회하면 본인 여부를 반환한다")
    fun publicProfileIdentifiesAuthenticatedOwner() {
        mockMvc.perform(get(
            "/api/v1/library/games/profile/{userId}",
            checkNotNull(owner.id)
        )
            .with(ownerAuthentication()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId")
                .value(checkNotNull(owner.id)))
            .andExpect(jsonPath("$.data.isMe").value(true))
            .andExpect(jsonPath("$.data.isFollowing").value(false))
    }

    @Test
    @DisplayName("비로그인 사용자도 다른 사용자의 게임 목록을 조회할 수 있다")
    fun publicGameListAllowsAnonymousUser() {
        val targetUserId = checkNotNull(other.id)

        mockMvc.perform(get(
            "/api/v1/library/games/profile/{userId}/games",
            targetUserId
        )
            .param("status", "ALL")
            .param("sort", "RECENT_PLAYED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.userGames[0].title")
                .value("Other"))
    }

    @Test
    fun rejectsInvalidParameters() {
        val invalidParameters = listOf(
            "status" to "INVALID",
            "sort" to "INVALID",
            "page" to "-1",
            "size" to "0",
            "size" to "101",
            "platformIds" to "-1",
            "genreIds" to "text",
        )
        for ((name, value) in invalidParameters) {
            mockMvc.perform(
                get("/api/v1/library/games")
                    .with(ownerAuthentication())
                    .param(name, value),
            ).andExpect(status().isBadRequest())
        }
    }

    private fun search(request: UserGameSearchRequest): Page<UserGameListResponse> {
        return service.getUserGameList(checkNotNull(owner.id), request)
    }

    private fun ownerAuthentication() =
        user(SecurityUser(checkNotNull(owner.id), emptyList()))

    private fun save(
        title: String,
        user: User = owner,
        status: PlayStatus? = null,
        playing: Boolean = false,
        backlog: Boolean = false,
        wishlist: Boolean = false,
        liked: Boolean = false,
        platform: Platform? = null,
        hours: Int? = null,
        day: Int? = null,
        rating: String? = null,
        genres: List<Genre> = emptyList(),
    ): UserGame {
        val game = Game.createFromIgdb(
            IgdbGameResponse(nextIgdbId++, title, null, null, null, null,
                mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()),
        )
        em.persist(game)
        genres.forEach { genre -> em.persist(GameGenre(game, genre)) }
        // 지원 플랫폼과 사용자 기록의 플랫폼이 달라도 검색 조건은 사용자 기록을 따른다.
        em.persist(GamePlatform(game, pc))
        val record = UserGame(
            user = user,
            game = game,
            playStatus = status,
            playing = playing,
            backlog = backlog,
            wishlist = wishlist,
            liked = liked,
            platform = platform,
            playTimeHours = hours?.let { BigDecimal.valueOf(it.toLong()) },
            finishTimeHours = null,
            masterTimeHours = null,
            startedAt = null,
            completedAt = null,
            lastPlayedAt = day?.let { LocalDateTime.of(2026, 9, it, 12, 0) },
        )
        em.persist(record)
        if (rating != null) em.persist(Review(record, BigDecimal(rating), null, false))
        return record
    }
}
