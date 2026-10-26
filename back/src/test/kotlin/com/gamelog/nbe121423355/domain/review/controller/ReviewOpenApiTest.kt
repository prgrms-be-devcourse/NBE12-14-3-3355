package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class ReviewOpenApiTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    @DisplayName("생성된 리뷰 문서에 인증·버전 규칙과 주요 오류 응답이 포함된다")
    fun documentsReviewWriteContracts() {
        val paths = document().path("paths")
        for ((path, method) in listOf(
            "/api/v1/games/{gameId}/reviews" to "put",
            "/api/v1/user-games/{userGameId}/reviews" to "put",
            "/api/v1/reviews/{reviewId}" to "put",
            "/api/v1/reviews/{reviewId}" to "delete",
        )) {
            val operation = paths.path(path).path(method)
            assertThat(operation.path("description").asString()).contains("409-6")
            assertThat(operation.path("security").first().has("bearerAuth")).isTrue()
            for (code in listOf("200", "400", "401", "404", "409")) {
                assertThat(operation.path("responses").has(code))
                    .withFailMessage("%s %s: 응답 %s 누락", method, path, code).isTrue()
            }
            val responses = operation.path("responses")
            assertThat(responses.path("200").path("content").isEmpty).isFalse()
            assertThat(responses.path("409").path("description").asString()).contains("409-6")
            val example = responses.path("409").path("content").first()
                .path("examples").path("리뷰 버전 충돌").path("value")
            assertThat(example.path("resultCode").asString()).isEqualTo("409-6")
        }
        assertThat(paths.path("/api/v1/reviews/{reviewId}").path("put").path("responses").has("403")).isTrue()
    }

    @Test
    @DisplayName("삭제 버전은 문서상 필수 쿼리이며 DTO 버전과 빈 통합 리뷰 정책을 설명한다")
    fun documentsVersionAndEmptyReviewPolicy() {
        val doc = document()
        val delete = doc.path("paths").path("/api/v1/reviews/{reviewId}").path("delete")
        val version = delete.path("parameters").single { it.path("name").asString() == "version" }
        assertThat(version.path("in").asString()).isEqualTo("query")
        assertThat(version.path("required").asBoolean()).isTrue()
        assertThat(version.path("description").asString()).contains("409-6")
        val schemas = doc.path("components").path("schemas")
        assertThat(schemas.path("ReviewSaveRequest").path("properties").path("version").path("description").asString())
            .contains("409-6", "재작성")
        assertThat(schemas.path("DetailedReviewSaveRequest").path("properties").path("review").path("description").asString())
            .contains("유지", "삭제하지 않습니다")
        assertThat(schemas.path("ReviewResponse").path("properties").path("version").path("description").asString())
            .contains("409-6", "재조회")
    }

    @Test
    @DisplayName("신고 문서는 201·관리자 권한·일괄 처리 규칙과 오류 응답을 명시한다")
    fun documentsReportContracts() {
        val paths = document().path("paths")
        val create = paths.path("/api/v1/reviews/{reviewId}/reports").path("post")
        assertThat(create.path("responses").has("201")).isTrue()
        assertThat(create.path("description").asString()).contains("400-3", "409-1", "1~255")
        val list = paths.path("/api/v1/admin/review-reports").path("get")
        assertThat(list.path("description").asString()).contains("ADMIN", "신고 대상 리뷰 수", "sort")
        val filter = list.path("parameters").single { it.path("name").asString() == "status" }
        assertThat(filter.path("description").asString()).contains("PENDING", "APPROVED", "REJECTED")
        val update = paths.path("/api/v1/admin/review-reports/{reportId}/status").path("put")
        assertThat(update.path("description").asString()).contains("HIDDEN_BY_ADMIN", "일괄", "400-5", "409-4")
        assertThat(update.path("responses").path("404").path("description").asString()).contains("404-5")
        for (operation in listOf(create, list, update)) {
            assertThat(operation.path("security").first().has("bearerAuth")).isTrue()
            assertThat(operation.path("responses").has("400")).isTrue()
            assertThat(operation.path("responses").has("401")).isTrue()
        }
        for (operation in listOf(list, update)) {
            assertThat(operation.path("responses").path("403").path("description").asString()).contains("ADMIN")
        }
        assertThat(update.path("responses").path("409").path("description").asString()).contains("409-4")
    }

    @Test
    @DisplayName("리뷰 조회 문서는 로그인 조건과 빈 결과 200·단건 없음 404를 구분한다")
    fun documentsReviewReadContracts() {
        val paths = document().path("paths")
        val mine = paths.path("/api/v1/games/{gameId}/reviews/me").path("get")
        assertThat(mine.path("responses").path("200").path("description").asString()).contains("null", "404가 아님")
        assertThat(mine.path("responses").has("401")).isTrue()
        val single = paths.path("/api/v1/reviews/{reviewId}").path("get")
        assertThat(single.path("responses").has("401")).isTrue()
        assertThat(single.path("responses").path("404").path("description").asString()).contains("404-3")
        for (path in listOf("/api/v1/games/{gameId}/reviews", "/api/v1/users/{userId}/reviews")) {
            val operation = paths.path(path).path("get")
            assertThat(operation.path("description").asString()).contains("비로그인")
            assertThat(operation.path("responses").path("200").path("description").asString()).contains("빈 목록")
            assertThat(operation.path("security").isMissingNode || operation.path("security").isEmpty).isTrue()
        }
    }

    private fun document(): JsonNode {
        val response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk).andReturn().response
        return mapper.readTree(response.contentAsString)
    }
}
