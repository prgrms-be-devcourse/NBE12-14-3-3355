package com.gamelog.nbe121423355.domain.review.e2e

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
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
class ReviewE2ETest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var encoder: PasswordEncoder
    @Autowired private lateinit var transaction: TransactionTemplate
    @Autowired private lateinit var em: EntityManager

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val password = "review-e2e-password"
    private val userIds = mutableListOf<Long>()
    private val gameIds = mutableListOf<Long>()
    private lateinit var writer: User
    private lateinit var reporter: User
    private lateinit var admin: User
    private lateinit var game: Game
    private val gameId get() = checkNotNull(game.id)
    private val writerId get() = checkNotNull(writer.id)

    @BeforeEach
    fun setUp() {
        // HTTP 요청은 별도 스레드·트랜잭션에서 실행되므로 준비 데이터를 먼저 커밋한다.
        transaction.executeWithoutResult {
            val suffix = UUID.randomUUID().toString().take(8)
            writer = fixtureUser("writer", suffix)
            reporter = fixtureUser("reporter", suffix)
            admin = fixtureUser("admin", suffix).apply { promoteToAdmin() }
            for (user in listOf(writer, reporter, admin)) {
                em.persist(user)
                userIds += checkNotNull(user.id)
            }
            game = Game.createFromIgdb(IgdbGameResponse(
                987701L, "리뷰 E2E 게임", "test", null, null, null,
                mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
            ))
            em.persist(game)
            gameIds += gameId
        }
    }

    @AfterEach
    fun tearDown() {
        // 실제 HTTP로 커밋된 데이터는 테스트 롤백으로 제거되지 않는다.
        // 이 테스트의 사용자·게임에 속한 데이터만 FK 순서로 정리한다.
        transaction.executeWithoutResult {
            if (userIds.isNotEmpty()) {
                em.createQuery("delete from ReviewReport r where r.review.userGame.user.id in :ids")
                    .setParameter("ids", userIds).executeUpdate()
                em.createQuery("delete from Review r where r.userGame.user.id in :ids")
                    .setParameter("ids", userIds).executeUpdate()
                for (entity in listOf("RefreshToken", "UserGame")) {
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
    @DisplayName("실제 로그인 → 리뷰 작성·조회·수정·삭제 → 라이브러리 제외 → 재작성")
    fun reviewLifecycleOverHttp() {
        val token = login(writer)
        assertLibrarySize(token, 0)

        // 별도 플레이 기록 없이 리뷰만 작성해도 라이브러리에 포함된다.
        val created = createReview(token)
        val reviewId = requiredLong(created.path("review"), "reviewId")
        val recordId = requiredLong(created.path("userGame"), "id")
        assertThat(created.path("userGame").path("inLibrary").asBoolean()).isTrue()
        assertLibrarySize(token, 1)

        val fetched = request("GET", "/api/v1/reviews/$reviewId", token).path("data")
        assertThat(fetched.path("content").asString()).isEqualTo("최초 리뷰")
        val oldVersion = requiredLong(fetched, "version")
        val mine = request("GET", "/api/v1/games/$gameId/reviews/me", token).path("data")
        assertThat(requiredLong(mine.path("review"), "reviewId")).isEqualTo(reviewId)
        assertThat(requiredLong(mine.path("userGame"), "id")).isEqualTo(recordId)

        val updated = request("PUT", "/api/v1/reviews/$reviewId", token,
            ReviewSaveRequest(oldVersion, BigDecimal("5.0"), "수정한 리뷰", true)).path("data")
        val updatedVersion = requiredLong(updated, "version")
        assertThat(updatedVersion).isGreaterThan(oldVersion)
        assertThat(updated.path("content").asString()).isEqualTo("수정한 리뷰")

        // 이전 화면이 갖고 있던 버전으로 저장해도 최신 내용이 덮어써지지 않는다.
        val stale = request("PUT", "/api/v1/reviews/$reviewId", token,
            ReviewSaveRequest(oldVersion, BigDecimal("1.0"), "오래된 화면", false), expectedStatus = 409)
        assertThat(stale.path("resultCode").asString()).isEqualTo("409-6")
        val latest = request("GET", "/api/v1/reviews/$reviewId", token).path("data")
        assertThat(latest.path("content").asString()).isEqualTo("수정한 리뷰")
        assertThat(requiredLong(latest, "version")).isEqualTo(updatedVersion)
        assertPublicReviewCount(1)

        request("DELETE", "/api/v1/reviews/$reviewId?version=$updatedVersion", token)
        assertLibrarySize(token, 0)
        assertPublicReviewCount(0)
        request("GET", "/api/v1/reviews/$reviewId", token, expectedStatus = 404)
        transaction.executeWithoutResult {
            assertThat(em.find(Review::class.java, reviewId).status).isEqualTo(ReviewStatus.DELETED_BY_USER)
            assertThat(em.find(UserGame::class.java, recordId).isInLibrary).isFalse()
        }

        // 삭제 후 의도적 재작성에는 버전을 생략하고 기존 리뷰·기록을 복구한다.
        val restored = request("PUT", "/api/v1/user-games/$recordId/reviews", token,
            ReviewSaveRequest(BigDecimal("4.5"), "다시 작성한 리뷰", false)).path("data")
        assertThat(requiredLong(restored, "reviewId")).isEqualTo(reviewId)
        assertThat(requiredLong(restored, "userGameId")).isEqualTo(recordId)
        assertThat(requiredLong(restored, "version")).isGreaterThan(updatedVersion)
        assertLibrarySize(token, 1)
        assertPublicReviewCount(1)
        val restoredRead = request("GET", "/api/v1/reviews/$reviewId", token).path("data")
        assertThat(restoredRead.path("content").asString()).isEqualTo("다시 작성한 리뷰")
        transaction.executeWithoutResult {
            assertThat(em.find(Review::class.java, reviewId).status).isEqualTo(ReviewStatus.ACTIVE)
            assertThat(em.find(UserGame::class.java, recordId).isInLibrary).isTrue()
        }
    }

    @Test
    @DisplayName("사용자 신고 → 관리자 로그인·승인 → 공개 리뷰 제외 및 신고 원문 보존")
    fun reportApprovalOverHttp() {
        val writerToken = login(writer)
        val created = createReview(writerToken)
        val reviewId = requiredLong(created.path("review"), "reviewId")
        val recordId = requiredLong(created.path("userGame"), "id")
        assertPublicReviewCount(1)
        assertLibrarySize(writerToken, 1)

        val reporterToken = login(reporter)
        val reported = request("POST", "/api/v1/reviews/$reviewId/reports", reporterToken,
            mapOf("reason" to "부적절한 리뷰"), expectedStatus = 201).path("data")
        val reportId = requiredLong(reported, "reportId")
        assertThat(reported.path("status").asString()).isEqualTo("PENDING")
        assertThat(reported.path("reviewContent").asString()).isEqualTo("최초 리뷰")

        val statusPath = "/api/v1/admin/review-reports/$reportId/status"
        request("PUT", statusPath, reporterToken, mapOf("status" to "APPROVED"), expectedStatus = 403)
        val adminToken = login(admin)
        val pending = request("GET", "/api/v1/admin/review-reports?status=PENDING", adminToken).path("data")
        assertThat(pending.path("reports").toList().map { requiredLong(it, "reviewId") }).contains(reviewId)

        val approved = request("PUT", statusPath, adminToken, mapOf("status" to "APPROVED")).path("data")
        assertThat(approved.path("status").asString()).isEqualTo("APPROVED")
        assertThat(approved.path("reviewStatus").asString()).isEqualTo("HIDDEN_BY_ADMIN")
        assertPublicReviewCount(0)
        request("GET", "/api/v1/reviews/$reviewId", writerToken, expectedStatus = 404)
        assertLibrarySize(writerToken, 0)

        val processed = request("GET", "/api/v1/admin/review-reports?status=APPROVED", adminToken)
            .path("data").path("reports").single { requiredLong(it, "reviewId") == reviewId }
        assertThat(processed.path("reviewContent").asString()).isEqualTo("최초 리뷰")
        assertThat(processed.path("reports").single().path("reason").asString()).isEqualTo("부적절한 리뷰")
        transaction.executeWithoutResult {
            assertThat(em.find(ReviewReport::class.java, reportId).status).isEqualTo(ReportStatus.APPROVED)
            assertThat(em.find(Review::class.java, reviewId).status).isEqualTo(ReviewStatus.HIDDEN_BY_ADMIN)
            assertThat(em.find(UserGame::class.java, recordId).isInLibrary).isFalse()
        }
    }

    private fun fixtureUser(role: String, suffix: String) =
        User("re2e-$role-$suffix", "review-e2e-$role-$suffix@test.com",
            checkNotNull(encoder.encode(password)) { "테스트 비밀번호 인코딩에 실패했습니다." })

    private fun login(user: User): String {
        val result = request("POST", "/api/v1/users/login",
            body = mapOf("email" to user.email, "password" to password))
        return result.path("data").path("accessToken").asString().also { assertThat(it).isNotBlank() }
    }

    private fun createReview(token: String): JsonNode {
        val emptyRecord = UserGameReqBody(
            playStatus = null,
            isPlaying = false,
            isBacklog = false,
            isWishlist = false,
            isLiked = false,
            platformId = null,
            playTimeHours = null,
            finishTimeHours = null,
            masterTimeHours = null,
            startedAt = null,
            completedAt = null,
            lastPlayedAt = null,
        )
        return request("PUT", "/api/v1/games/$gameId/reviews", token,
            DetailedReviewSaveRequest(emptyRecord, ReviewSaveRequest(BigDecimal("4.0"), "최초 리뷰", false)))
            .path("data")
    }

    private fun assertLibrarySize(token: String, expected: Long) {
        val data = request("GET", "/api/v1/library/games", token).path("data")
        assertThat(requiredLong(data, "totalElements")).isEqualTo(expected)
        if (expected == 1L) {
            assertThat(requiredLong(data.path("userGames").single(), "gameId")).isEqualTo(gameId)
        } else assertThat(data.path("userGames").isEmpty).isTrue()
    }

    private fun assertPublicReviewCount(expected: Long) {
        for (path in listOf("/api/v1/games/$gameId/reviews", "/api/v1/users/$writerId/reviews")) {
            val data = request("GET", path).path("data")
            assertThat(requiredLong(data, "totalElements")).isEqualTo(expected)
            assertThat(data.path("reviews").size()).isEqualTo(expected.toInt())
        }
    }

    private fun requiredLong(node: JsonNode, field: String): Long {
        assertThat(node.path(field).isIntegralNumber).withFailMessage("필수 숫자 필드 누락: %s, %s", field, node).isTrue()
        return node.path(field).asLong()
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
        assertThat(response.statusCode()).withFailMessage("%s %s: %s", method, path, response.body())
            .isEqualTo(expectedStatus)
        val result = mapper.readTree(response.body())
        assertThat(result.path("resultCode").asString()).startsWith("$expectedStatus-")
        return result
    }
}
