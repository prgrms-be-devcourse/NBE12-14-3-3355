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
        save(owner, "가 게임", PlayStatus.COMPLETED, false, false, false, pc, 10, 1, "4.5", listOf(rpg, action))
        save(owner, "나 게임", null, true, true, false, console, 20, 2, "2.0", listOf(action))
        save(owner, "다 100%_!", null, false, false, true, null, null, null, null, listOf())
        other = User("other", "other@test.com", "password")
        em.persist(other)
        save(other, "Other", PlayStatus.PLAYED, true, true, true, pc, 100, 3, "5.0", listOf(rpg))
        val removed = save(owner, "Removed", null, false, false, false, null, null, null, null, listOf())
        removed.updatePlayRecord(null, false, false, false, true, null, null, null, null, null, null, null)
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
        for (tab in listOf(UserGameTab.PLAYED, UserGameTab.PLAYING, UserGameTab.BACKLOG, UserGameTab.WISHLIST, UserGameTab.LIKED)) {
            val request = UserGameSearchRequest()
            request.status = tab
            val expected = when (tab) {
                UserGameTab.PLAYED -> "가 게임"
                UserGameTab.PLAYING, UserGameTab.BACKLOG -> "나 게임"
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
    fun treatsWildcardsAsLiteralTextAndEmptyFiltersAsAll() {
        val request = UserGameSearchRequest()
        request.keyword = " %_! "
        request.genreIds = listOf()
        request.platformIds = listOf()
        assertThat(search(request).content.map { it.title }).containsExactly("다 100%_!")
        request.keyword = "  "
        assertThat(search(request).totalElements).isEqualTo(3L)
    }

    @Test
    fun sortsByPersonalRatingTitleAndPlayTimeWithNullsLast() {
        val request = UserGameSearchRequest()
        for (sort in listOf(UserGameSort.RATING, UserGameSort.TITLE)) {
            request.sort = sort
            assertThat(search(request).content.map { it.title })
                .containsExactly("가 게임", "나 게임", "다 100%_!")
        }
        request.sort = UserGameSort.PLAY_TIME
        assertThat(search(request).content.map { it.title })
            .containsExactly("나 게임", "가 게임", "다 100%_!")
    }

    @Test
    fun endpointBindsFiltersAndPreservesResponseShape() {
        mockMvc.perform(get("/api/v1/library/games")
            .with(user(SecurityUser(checkNotNull(owner.id), listOf())))
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
        // given: 프로필 통계를 가진 다른 사용자를 준비
        val targetUserId = checkNotNull(other.id)

        // when: 인증 정보 없이 다른 사용자의 프로필을 조회
        mockMvc.perform(get(
            "/api/v1/library/games/profile/{userId}",
            targetUserId
        ))
        // then: 대상 사용자 ID와 공개 조회 상태를 반환하는지 확인
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
        // given: 프로필 소유자의 인증 정보를 준비
        val loginUser = SecurityUser(
            checkNotNull(owner.id),
            listOf()
        )

        // when: 본인의 사용자 ID가 포함된 공개 프로필 경로를 조회
        mockMvc.perform(get(
            "/api/v1/library/games/profile/{userId}",
            checkNotNull(owner.id)
        )
            .with(user(loginUser)))
        // then: 본인 프로필로 판별하는지 확인
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId")
                .value(checkNotNull(owner.id)))
            .andExpect(jsonPath("$.data.isMe").value(true))
            .andExpect(jsonPath("$.data.isFollowing").value(false))
    }

    @Test
    @DisplayName("비로그인 사용자도 다른 사용자의 게임 목록을 조회할 수 있다")
    fun publicGameListAllowsAnonymousUser() {
        // given: 다른 사용자의 게임 목록과 조회 조건을 준비
        val targetUserId = checkNotNull(other.id)

        // when: 인증 정보 없이 다른 사용자의 게임 목록을 조회
        mockMvc.perform(get(
            "/api/v1/library/games/profile/{userId}/games",
            targetUserId
        )
            .param("status", "ALL")
            .param("sort", "RECENT_PLAYED"))
        // then: 대상 사용자의 게임과 전체 개수를 반환하는지 확인
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
                    .with(user(SecurityUser(checkNotNull(owner.id), listOf())))
                    .param(name, value),
            ).andExpect(status().isBadRequest())
        }
    }

    private fun search(request: UserGameSearchRequest): Page<UserGameListResponse> {
        return service.getUserGameList(checkNotNull(owner.id), request)
    }

    private fun save(
        user: User, title: String, status: PlayStatus?, playing: Boolean, backlog: Boolean,
        wishlist: Boolean, platform: Platform?, hours: Int?, day: Int?, rating: String?, genres: List<Genre>,
    ): UserGame {
        val game = Game.createFromIgdb(IgdbGameResponse(nextIgdbId++, title, null, null, null, null,
            mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()))
        em.persist(game)
        genres.forEach { genre -> em.persist(GameGenre(game, genre)) }
        // Both games support PC, but only one user record uses PC.
        em.persist(GamePlatform(game, pc))
        val record = UserGame(user, game, status, playing, backlog, wishlist, false, platform,
            hours?.let { BigDecimal.valueOf(it.toLong()) }, null, null, null, null,
            day?.let { LocalDateTime.of(2026, 9, it, 12, 0) })
        em.persist(record)
        if (rating != null) em.persist(Review(record, BigDecimal(rating), null, false))
        return record
    }
}
