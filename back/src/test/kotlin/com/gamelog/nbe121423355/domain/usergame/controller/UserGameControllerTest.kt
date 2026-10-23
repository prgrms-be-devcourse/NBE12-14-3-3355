package com.gamelog.nbe121423355.domain.usergame.controller

import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.domain.usergame.support.apiGame
import com.gamelog.nbe121423355.domain.usergame.support.recordRequest
import com.gamelog.nbe121423355.global.security.SecurityUser
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class UserGameControllerTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var records: UserGameRepository

    private lateinit var owner: User
    private lateinit var other: User
    private lateinit var game: Game
    private lateinit var freshGame: Game
    private lateinit var pc: Platform
    private lateinit var rpg: Genre
    private lateinit var action: Genre
    private val ownerId get() = checkNotNull(owner.id)
    private val gameId get() = checkNotNull(game.id)
    private val freshGameId get() = checkNotNull(freshGame.id)
    private val base = "/api/v1/library/games"

    @BeforeEach
    fun setUp() {
        owner = User("controller-owner", "controller-owner@test.com", "password")
        other = User("controller-other", "controller-other@test.com", "password")
        game = apiGame(981001L, "기록 게임")
        freshGame = apiGame(981002L, "새 게임")
        val otherGame = apiGame(981003L, "Other")
        pc = Platform.createFromIgdb(981001L, "Controller PC")
        rpg = Genre.createFromIgdb(981001L, "Controller RPG")
        action = Genre.createFromIgdb(981002L, "Controller Action")
        listOf(owner, other, game, freshGame, otherGame, pc, rpg, action).forEach(em::persist)
        em.persist(GameGenre(game, rpg))
        em.persist(GameGenre(game, action))
        val record = UserGame(
            owner, game, PlayStatus.PLAYED, false, false, false, false, pc,
            BigDecimal.TEN, null, null, null, null, LocalDateTime.of(2026, 8, 1, 12, 0),
        )
        em.persist(record)
        em.persist(Review(record, BigDecimal("4.5"), "리뷰", false))
        em.persist(UserGame(other, otherGame).apply { changePlayStatus(PlayStatus.PLAYED) })
        em.flush()
        em.clear()
    }

    @Test
    @DisplayName("목록 API는 필터를 바인딩하고 페이지 응답을 반환한다")
    fun endpointBindsFiltersAndPreservesResponseShape() {
        mvc.perform(get(base).with(auth())
            .param("status", "PLAYED").param("sort", "RATING")
            .param("genreIds", "${rpg.id},${action.id}")
            .param("platformIds", pc.id.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.userGames[0].gameId").value(gameId))
            .andExpect(jsonPath("$.data.userGames[0].title").value("기록 게임"))
    }

    @Test
    @DisplayName("새 기록 등록은 201과 저장한 기록을 반환한다")
    fun createsRecord() {
        mvc.perform(post("$base/$freshGameId").with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(recordRequest(liked = true, hours = BigDecimal("12.5")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resultCode").value("201-1"))
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.userId").value(ownerId))
            .andExpect(jsonPath("$.data.gameId").value(freshGameId))
            .andExpect(jsonPath("$.data.playStatus").value("PLAYED"))
            .andExpect(jsonPath("$.data.playing").value(false))
            .andExpect(jsonPath("$.data.liked").value(true))
            .andExpect(jsonPath("$.data.playTimeHours").value(12.5))
            .andExpect(jsonPath("$.data.inLibrary").value(true))
        assertThat(reload(freshGameId).playTimeHours).isEqualByComparingTo("12.5")
    }

    @Test
    @DisplayName("같은 게임을 다시 등록하면 기존 기록을 수정하고 200을 반환한다")
    fun updatesExistingRecordThroughPost() {
        val existingId = reload(gameId).id
        mvc.perform(post("$base/$gameId").with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(recordRequest(playStatus = PlayStatus.COMPLETED))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.id").value(existingId))
            .andExpect(jsonPath("$.data.playStatus").value("COMPLETED"))
        assertThat(reload(gameId).id).isEqualTo(existingId)
    }

    @Test
    @DisplayName("PATCH는 기록의 시간과 날짜 및 플랫폼을 수정한다")
    fun updatesPlayRecord() {
        val started = LocalDate.of(2026, 8, 1)
        val completed = started.plusDays(2)
        val lastPlayed = completed.atTime(12, 0)
        mvc.perform(patch("$base/$gameId").with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(recordRequest(
                playStatus = PlayStatus.COMPLETED, playing = true, liked = true, platformId = pc.id,
                hours = BigDecimal("25.5"), finishHours = BigDecimal("20"), masterHours = BigDecimal("30"),
                startedAt = started, completedAt = completed, lastPlayedAt = lastPlayed,
            ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.platformId").value(pc.id))
            .andExpect(jsonPath("$.data.playing").value(true))
            .andExpect(jsonPath("$.data.liked").value(true))
            .andExpect(jsonPath("$.data.playTimeHours").value(25.5))
            .andExpect(jsonPath("$.data.finishTimeHours").value(20))
            .andExpect(jsonPath("$.data.masterTimeHours").value(30))
            .andExpect(jsonPath("$.data.startedAt").value(started.toString()))
            .andExpect(jsonPath("$.data.completedAt").value(completed.toString()))
            .andExpect(jsonPath("$.data.lastPlayedAt").value("2026-08-03T12:00:00"))
        val saved = reload(gameId)
        assertThat(saved.playTimeHours).isEqualByComparingTo("25.5")
        assertThat(saved.completedAt).isEqualTo(completed)
        assertThat(saved.playStatus).isEqualTo(PlayStatus.COMPLETED)
        assertThat(saved.playing).isTrue()
        assertThat(saved.liked).isTrue()
    }

    @Test
    @DisplayName("미등록 게임의 기록 수정은 404를 반환한다")
    fun rejectsPatchWithoutRecord() {
        mvc.perform(patch("$base/$freshGameId").with(auth())
            .contentType(MediaType.APPLICATION_JSON).content(json(recordRequest())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.resultCode").value("404-3"))
            .andExpect(jsonPath("$.msg").value("라이브러리에 등록되지 않은 게임입니다."))
    }

    @Test
    @DisplayName("존재하지 않는 게임 등록은 404를 반환한다")
    fun rejectsMissingGame() {
        mvc.perform(post("$base/${Long.MAX_VALUE}").with(auth())
            .contentType(MediaType.APPLICATION_JSON).content(json(recordRequest())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.resultCode").value("404-2"))
    }

    @Test
    @DisplayName("플레이 상태를 지정하면 해당 상태를 반환한다")
    fun changesPlayStatus() {
        mvc.perform(put("$base/$gameId/play-status").with(auth()).param("status", "COMPLETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.playStatus").value("COMPLETED"))
        assertThat(reload(gameId).playStatus).isEqualTo(PlayStatus.COMPLETED)
    }

    @Test
    @DisplayName("플레이 상태를 생략하면 기존 상태를 해제한다")
    fun clearsPlayStatusWhenOmitted() {
        mvc.perform(put("$base/$gameId/play-status").with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.playStatus").value(nullValue()))
        assertThat(reload(gameId).playStatus).isNull()
    }

    @Test
    @DisplayName("playing 파라미터를 상태 응답과 DB에 반영한다")
    fun changesPlaying() {
        mvc.perform(patch("$base/$gameId/playing").with(auth()).param("playing", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(true))
        assertThat(reload(gameId).playing).isTrue()
    }

    @Test
    @DisplayName("wishlist 파라미터를 상태 응답과 DB에 반영한다")
    fun changesWishlist() {
        mvc.perform(patch("$base/$gameId/wishlist").with(auth()).param("wishlist", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(true))
        assertThat(reload(gameId).wishlist).isTrue()
    }

    @Test
    @DisplayName("backlog 파라미터를 상태 응답과 DB에 반영한다")
    fun changesBacklog() {
        mvc.perform(patch("$base/$gameId/backlog").with(auth()).param("backlog", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(true))
        assertThat(reload(gameId).backlog).isTrue()
    }

    @Test
    @DisplayName("liked 파라미터를 상태 응답과 DB에 반영한다")
    fun changesLiked() {
        mvc.perform(patch("$base/$gameId/liked").with(auth()).param("liked", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(true))
        assertThat(reload(gameId).liked).isTrue()
    }

    @Test
    @DisplayName("내 프로필은 본인 여부와 통계 및 최근 기록을 반환한다")
    fun getsOwnProfile() {
        mvc.perform(get("$base/profile").with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(ownerId))
            .andExpect(jsonPath("$.data.isMe").value(true))
            .andExpect(jsonPath("$.data.isFollowing").value(false))
            .andExpect(jsonPath("$.data.stats.playedGameCount").value(1))
            .andExpect(jsonPath("$.data.recentGames[0].gameId").value(gameId))
            .andExpect(jsonPath("$.data.recentReviews[0].gameId").value(gameId))
    }

    @Test
    @DisplayName("비로그인 사용자도 다른 사용자의 프로필 통계를 조회할 수 있다")
    fun publicProfileAllowsAnonymousUser() {
        mvc.perform(get("$base/profile/${other.id}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.userId").value(other.id))
            .andExpect(jsonPath("$.data.isMe").value(false))
            .andExpect(jsonPath("$.data.isFollowing").value(false))
            .andExpect(jsonPath("$.data.stats.playedGameCount").value(1))
    }

    @Test
    @DisplayName("본인의 공개 프로필을 조회해도 본인으로 판별한다")
    fun publicProfileIdentifiesAuthenticatedOwner() {
        mvc.perform(get("$base/profile/$ownerId").with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(ownerId))
            .andExpect(jsonPath("$.data.isMe").value(true))
            .andExpect(jsonPath("$.data.isFollowing").value(false))
    }

    @Test
    @DisplayName("비로그인 사용자도 대상 사용자의 게임 목록을 조회할 수 있다")
    fun publicGameListAllowsAnonymousUser() {
        mvc.perform(get("$base/profile/${other.id}/games")
            .param("status", "ALL").param("sort", "RECENT_PLAYED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.userGames[0].title").value("Other"))
    }

    @Test
    @DisplayName("없는 사용자의 공개 프로필과 목록은 404를 반환한다")
    fun rejectsMissingPublicUser() {
        for (suffix in listOf("", "/games")) {
            mvc.perform(get("$base/profile/${Long.MAX_VALUE}$suffix"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
        }
    }

    @Test
    @DisplayName("최근 게임 API는 최근 기록을 JSON 목록으로 반환한다")
    fun getsRecentGames() {
        mvc.perform(get("$base/recentGames").with(auth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].gameId").value(gameId))
    }

    @Test
    @DisplayName("인생게임은 요청 순서대로 저장하고 반환한다")
    fun updatesFavoriteGames() {
        em.persist(UserGame(em.getReference(User::class.java, ownerId),
            em.getReference(Game::class.java, freshGameId)).apply { changeWishlist(true) })
        em.flush()
        mvc.perform(put("$base/favorite-games").with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(mapOf("gameIds" to listOf(freshGameId, gameId)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data[0].gameId").value(freshGameId))
            .andExpect(jsonPath("$.data[0].displayOrder").value(1))
            .andExpect(jsonPath("$.data[1].gameId").value(gameId))
            .andExpect(jsonPath("$.data[1].displayOrder").value(2))
    }

    @Test
    @DisplayName("라이브러리 밖 게임을 인생게임으로 지정하면 400을 반환한다")
    fun rejectsFavoriteOutsideLibrary() {
        mvc.perform(put("$base/favorite-games").with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(mapOf("gameIds" to listOf(freshGameId)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.resultCode").value("400-11"))
    }

    @Test
    @DisplayName("보호된 API는 비로그인 요청에 401을 반환한다")
    fun requiresAuthentication() {
        val routes = listOf(
            HttpMethod.GET to base,
            HttpMethod.POST to "$base/$freshGameId",
            HttpMethod.PATCH to "$base/$gameId",
            HttpMethod.PUT to "$base/$gameId/play-status",
            HttpMethod.PATCH to "$base/$gameId/playing",
            HttpMethod.PATCH to "$base/$gameId/wishlist",
            HttpMethod.PATCH to "$base/$gameId/backlog",
            HttpMethod.PATCH to "$base/$gameId/liked",
            HttpMethod.GET to "$base/profile",
            HttpMethod.GET to "$base/recentGames",
            HttpMethod.PUT to "$base/favorite-games",
        )
        for ((method, path) in routes) {
            mvc.perform(request(method, path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resultCode").value("401-1"))
        }
    }

    @Test
    @DisplayName("잘못된 검색 파라미터는 400을 반환한다")
    fun rejectsInvalidParameters() {
        val parameters = listOf(
            "status" to "INVALID", "sort" to "INVALID", "page" to "-1",
            "size" to "0", "size" to "101", "platformIds" to "-1", "genreIds" to "text",
        )
        for ((name, value) in parameters) {
            mvc.perform(get(base).with(auth()).param(name, value))
                .andExpect(status().isBadRequest())
        }
    }

    @Test
    @DisplayName("Boolean 상태 파라미터가 없거나 잘못된 형식이면 400을 반환한다")
    fun rejectsInvalidBooleanParameters() {
        for (name in listOf("playing", "wishlist", "backlog", "liked")) {
            mvc.perform(patch("$base/$gameId/$name").with(auth()))
                .andExpect(status().isBadRequest())
            mvc.perform(patch("$base/$gameId/$name").with(auth()).param(name, "invalid"))
                .andExpect(status().isBadRequest())
        }
    }

    @Test
    @DisplayName("음수 플레이 시간은 검증 오류로 거절한다")
    fun rejectsNegativeHours() {
        val invalid = listOf(
            recordRequest(hours = BigDecimal("-1")),
            recordRequest(finishHours = BigDecimal("-1")),
            recordRequest(masterHours = BigDecimal("-1")),
        )
        for (body in invalid) {
            mvc.perform(post("$base/$freshGameId").with(auth())
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.msg").value(containsString("PositiveOrZero")))
        }
        assertThat(records.findByUser_IdAndGame_Id(ownerId, freshGameId)).isNull()
    }

    @Test
    @DisplayName("잘못된 JSON과 enum 및 필수 필드 누락은 400을 반환한다")
    fun rejectsMalformedRecordBodies() {
        val valid = mapper.readTree(json(recordRequest()))
        val bodies = listOf(
            "{",
            json((valid.deepCopy() as tools.jackson.databind.node.ObjectNode).put("playStatus", "UNKNOWN")),
            json(mapOf("playStatus" to "PLAYED")),
        )
        for (body in bodies) {
            mvc.perform(post("$base/$freshGameId").with(auth())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-2"))
        }
    }

    @Test
    @DisplayName("시작일 이전의 완료일은 서비스 오류 코드로 반환한다")
    fun rejectsInvalidDateOrder() {
        val started = LocalDate.of(2026, 8, 2)
        mvc.perform(post("$base/$freshGameId").with(auth())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(recordRequest(startedAt = started, completedAt = started.minusDays(1)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.resultCode").value("400-6"))
    }

    private fun auth() = user(SecurityUser(ownerId, emptyList()))
    private fun json(value: Any): String = mapper.writeValueAsString(value)

    private fun reload(targetGameId: Long): UserGame {
        em.flush()
        em.clear()
        return checkNotNull(records.findByUser_IdAndGame_Id(ownerId, targetGameId))
    }
}
