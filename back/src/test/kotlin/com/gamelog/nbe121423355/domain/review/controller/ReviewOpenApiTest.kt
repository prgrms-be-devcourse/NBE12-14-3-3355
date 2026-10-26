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

    private fun document(): JsonNode {
        val response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk).andReturn().response
        return mapper.readTree(response.contentAsString)
    }
}
