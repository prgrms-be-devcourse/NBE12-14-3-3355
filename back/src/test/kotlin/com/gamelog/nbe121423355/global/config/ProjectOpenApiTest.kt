package com.gamelog.nbe121423355.global.config

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
class ProjectOpenApiTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    @DisplayName("문서의 모든 API에는 요약·그룹이 있고 인증 주체가 입력 파라미터로 노출되지 않는다")
    fun documentsOperationsAndHidesPrincipals() {
        val paths = document().path("paths")
        val methods = listOf("get", "post", "put", "patch", "delete")
        val missingSuccess = mutableListOf<String>()
        for (path in paths) {
            for (method in methods) {
                val operation = path.path(method)
                if (operation.isMissingNode) continue
                assertThat(operation.path("summary").asString()).isNotBlank()
                assertThat(operation.path("tags").isEmpty).isFalse()
                if ((200..299).none { operation.path("responses").has(it.toString()) }) {
                    missingSuccess.add(operation.path("operationId").asString())
                }
                val names = operation.path("parameters").toList().map { it.path("name").asString() }
                assertThat(names).doesNotContain("securityUser", "user", "adminUser", "httpServletResponse")
            }
        }
        assertThat(missingSuccess).withFailMessage("성공 응답 문서 누락: %s", missingSuccess).isEmpty()
    }

    @Test
    @DisplayName("생성과 비동기 접수의 201·202 및 기록 등록의 200·201이 문서에 구분된다")
    fun documentsActualSuccessStatuses() {
        val doc = document()
        for ((path, code) in listOf(
            "/api/v1/users/signup" to "201",
            "/api/v1/library/games/{gameId}" to "201",
            "/api/v1/reviews/{reviewId}/comments" to "201",
            "/api/v1/reviews/{reviewId}/likes" to "201",
            "/api/v1/admin/igdb-sync" to "202",
            "/api/v1/admin/related-game-precompute/runs" to "202",
        )) {
            assertThat(operation(doc, path, "post").path("responses").has(code))
                .withFailMessage("%s: HTTP %s 문서 누락", path, code).isTrue()
        }
        assertThat(operation(doc, "/api/v1/library/games/{gameId}", "post").path("responses").has("200")).isTrue()
    }

    @Test
    @DisplayName("관리자와 알림 API는 bearerAuth를 표시하고 공개 댓글 조회에는 인증을 강제하지 않는다")
    fun documentsSecurityRequirements() {
        val doc = document()
        for ((path, method) in listOf(
            "/api/v1/admin/igdb-sync" to "post",
            "/api/v1/admin/related-game-precompute/runs" to "post",
            "/api/v1/admin/related-game-precompute/status" to "get",
            "/api/v1/notifications" to "get",
            "/api/v1/notifications/{id}/read" to "put",
        )) {
            val op = operation(doc, path, method)
            assertThat(op.path("security").first().has("bearerAuth")).isTrue()
            assertThat(op.path("responses").has("401")).isTrue()
        }
        assertThat(operation(doc, "/api/v1/admin/related-game-precompute/runs", "post")
            .path("responses").path("403").path("description").asString()).contains("ADMIN")
        val security = operation(doc, "/api/v1/reviews/{reviewId}/comments", "get").path("security")
        assertThat(security.isMissingNode || security.isEmpty).isTrue()
    }

    @Test
    @DisplayName("로그인·재발급·로그아웃 문서는 Set-Cookie와 토큰 사용법을 설명한다")
    fun documentsTokenCookies() {
        val doc = document()
        for (path in listOf("/api/v1/users/login", "/api/v1/users/refresh", "/api/v1/users/logout")) {
            val op = operation(doc, path, "post")
            assertThat(op.path("responses").path("200").path("headers").has("Set-Cookie")).isTrue()
            assertThat(op.path("description").asString()).contains("쿠키")
        }
        assertThat(operation(doc, "/api/v1/users/refresh", "post").path("description").asString())
            .contains("Rotation", "10초", "401-3")
    }

    @Test
    @DisplayName("검색 DTO는 개별 쿼리 파라미터로 노출되고 알림 커서 사용법이 표시된다")
    fun documentsSearchAndCursorParameters() {
        val doc = document()
        for (path in listOf("/api/v1/games/page", "/api/v1/library/games")) {
            val params = operation(doc, path, "get").path("parameters").toList()
            assertThat(params.map { it.path("name").asString() })
                .contains("page", "size", "keyword", "genreIds", "platformIds", "sort")
            assertThat(params.all { it.path("in").asString() == "query" }).isTrue()
        }
        val notifications = operation(doc, "/api/v1/notifications", "get")
        assertThat(notifications.path("description").asString()).contains("nextCursor", "20개")
        assertThat(notifications.path("parameters").toList().map { it.path("name").asString() })
            .contains("before", "unreadOnly")
    }

    @Test
    @DisplayName("업로드 문서는 파일 전송 방식·제한·외부 저장소 오류를 명시한다")
    fun documentsMultipartUpload() {
        val upload = operation(document(), "/api/v1/uploads/images", "post")
        assertThat(upload.path("description").asString()).contains("multipart/form-data", "file", "5MB")
        assertThat(upload.path("requestBody").path("content").has("multipart/form-data")).isTrue()
        assertThat(upload.path("responses").has("401")).isTrue()
        assertThat(upload.path("responses").path("500").path("description").asString()).contains("500-2")
    }

    private fun operation(doc: JsonNode, path: String, method: String): JsonNode {
        val result = doc.path("paths").path(path).path(method)
        assertThat(result.isMissingNode).withFailMessage("API 문서 누락: %s %s", method, path).isFalse()
        return result
    }

    private fun document(): JsonNode {
        val response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk).andReturn().response
        return mapper.readTree(response.contentAsString)
    }
}
