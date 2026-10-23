package com.gamelog.nbe121423355.domain.usergame.e2e

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.support.apiGame
import com.gamelog.nbe121423355.domain.usergame.support.recordRequest
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MySqlIntegrationTest
@Tag("e2e")
class UserGameE2ETest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var transaction: TransactionTemplate
    @Autowired private lateinit var em: EntityManager

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val base = "/api/v1/library/games"
    private val password = "password123"
    private lateinit var owner: User
    private lateinit var other: User
    private lateinit var game: Game
    private val userIds = mutableListOf<Long>()
    private val gameIds = mutableListOf<Long>()
    private val ownerId get() = checkNotNull(owner.id)
    private val gameId get() = checkNotNull(game.id)

    @BeforeEach
    fun setUp() {
        // 실제 HTTP 요청은 별도 스레드에서 실행되므로 fixture를 먼저 커밋한다.
        transaction.executeWithoutResult {
            owner = User("e2e-owner", "e2e-owner@test.com", passwordEncoder.encode(password))
            other = User("e2e-other", "e2e-other@test.com", passwordEncoder.encode(password))
            for (user in listOf(owner, other)) {
                em.persist(user)
                userIds += checkNotNull(user.id)
            }
            game = apiGame(982001L, "E2E 게임")
            em.persist(game)
            gameIds += gameId
        }
    }

    @AfterEach
    fun tearDown() {
        // 롤백으로 제거되지 않는 로그인 세션과 API 생성 데이터를 FK 순서로 정리한다.
        transaction.executeWithoutResult {
            if (userIds.isNotEmpty()) {
                for (entity in listOf("RefreshToken", "UserFavoriteGame", "UserGame")) {
                    em.createQuery("delete from $entity e where e.user.id in :ids")
                        .setParameter("ids", userIds).executeUpdate()
                }
                em.createQuery("delete from User u where u.id in :ids")
                    .setParameter("ids", userIds).executeUpdate()
            }
            if (gameIds.isNotEmpty()) {
                em.createQuery("delete from Game g where g.id in :ids")
                    .setParameter("ids", gameIds).executeUpdate()
            }
        }
    }

    @Test
    @DisplayName("실제 로그인 후 기록 등록·조회·수정을 수행하며 다른 사용자 기록과 분리한다")
    fun recordsCanBeCreatedReadAndUpdatedOverHttp() {
        request("GET", base, expectedStatus = 401)
        val token = login(owner)
        val otherToken = login(other)
        val created = request("POST", "$base/$gameId", token, recordRequest(
            hours = BigDecimal("12.5"), lastPlayedAt = LocalDateTime.of(2026, 8, 1, 12, 0),
        ), expectedStatus = 201)
        val recordId = created.path("data").path("id").asLong()
        assertThat(recordId).isPositive()
        assertThat(created.path("data").path("userId").asLong()).isEqualTo(ownerId)

        val library = request("GET", base, token).path("data")
        assertThat(library.path("totalElements").asLong()).isEqualTo(1)
        assertThat(library.path("userGames").single().path("gameId").asLong()).isEqualTo(gameId)

        val updated = request("PATCH", "$base/$gameId", token, recordRequest(
            playStatus = PlayStatus.COMPLETED, hours = BigDecimal("25"),
            lastPlayedAt = LocalDateTime.of(2026, 8, 2, 12, 0),
        )).path("data")
        assertThat(updated.path("id").asLong()).isEqualTo(recordId)
        assertThat(updated.path("playStatus").asString()).isEqualTo("COMPLETED")
        assertThat(updated.path("playTimeHours").decimalValue()).isEqualByComparingTo("25")

        val recent = request("GET", "$base/recentGames", token).path("data")
        assertThat(recent.single().path("gameId").asLong()).isEqualTo(gameId)
        val profile = request("GET", "$base/profile", token).path("data")
        assertThat(profile.path("isMe").asBoolean()).isTrue()
        assertThat(profile.path("stats").path("totalPlayTime").decimalValue()).isEqualByComparingTo("25")

        assertThat(request("GET", base, otherToken).path("data").path("totalElements").asLong()).isZero()
        request("PATCH", "$base/$gameId", otherToken, recordRequest(), expectedStatus = 404)
        val publicLibrary = request("GET", "$base/profile/$ownerId/games").path("data")
        assertThat(publicLibrary.path("userGames").single().path("gameId").asLong()).isEqualTo(gameId)
        assertThat(request("GET", "$base/profile/$ownerId").path("data").path("isMe").asBoolean()).isFalse()
    }

    @Test
    @DisplayName("마지막 기록을 해제하면 인생게임에서 제거되며 좋아요와 동일 기록은 유지된다")
    fun removingLastRecordClearsFavoriteAndPreservesLike() {
        val token = login(owner)
        val created = request("POST", "$base/$gameId", token,
            recordRequest(liked = true), expectedStatus = 201)
        val recordId = created.path("data").path("id").asLong()

        val favorites = request("PUT", "$base/favorite-games", token,
            mapOf("gameIds" to listOf(gameId))).path("data")
        assertThat(favorites.single().path("gameId").asLong()).isEqualTo(gameId)
        assertThat(request("GET", "$base/profile", token).path("data").path("favorite").size()).isEqualTo(1)

        val cleared = request("PUT", "$base/$gameId/play-status", token).path("data")
        assertThat(cleared.path("playStatus").isNull).isTrue()
        assertThat(request("GET", base, token).path("data").path("totalElements").asLong()).isZero()
        assertThat(request("GET", "$base/profile", token).path("data").path("favorite").isEmpty).isTrue()

        val likes = request("GET", "$base?status=LIKED", token).path("data").path("userGames")
        assertThat(likes.single().path("gameId").asLong()).isEqualTo(gameId)
        assertThat(likes.single().path("liked").asBoolean()).isTrue()
        request("PUT", "$base/favorite-games", token,
            mapOf("gameIds" to listOf(gameId)), expectedStatus = 400)

        val restored = request("POST", "$base/$gameId", token, recordRequest(liked = true)).path("data")
        assertThat(restored.path("id").asLong()).isEqualTo(recordId)
        assertThat(restored.path("inLibrary").asBoolean()).isTrue()
    }

    private fun login(user: User): String {
        val response = request("POST", "/api/v1/users/login",
            body = mapOf("email" to user.email, "password" to password))
        val token = response.path("data").path("accessToken").asString()
        assertThat(token).isNotBlank()
        return token
    }

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        body: Any? = null,
        expectedStatus: Int = 200,
    ): JsonNode {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else {
            builder.header("Content-Type", "application/json")
            HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))
        }
        val response = client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode())
            .withFailMessage("%s %s: %s", method, path, response.body())
            .isEqualTo(expectedStatus)
        val result = mapper.readTree(response.body())
        assertThat(result.path("resultCode").asString()).startsWith("$expectedStatus-")
        return result
    }
}
