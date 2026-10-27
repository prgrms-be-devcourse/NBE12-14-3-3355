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
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Page
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class UserGameSearchTest {
    @Autowired
    private lateinit var em: EntityManager
    @Autowired
    private lateinit var service: UserGameService
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

    private fun search(request: UserGameSearchRequest): Page<UserGameListResponse> {
        return service.getUserGameList(checkNotNull(owner.id), request)
    }

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
