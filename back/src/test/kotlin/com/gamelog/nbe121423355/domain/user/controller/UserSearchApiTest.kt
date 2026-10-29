package com.gamelog.nbe121423355.domain.user.controller

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

// 검색 조건·페이징 로직은 UserSearchIntegrationTest(서비스)에서 검증하고, 여기서는 HTTP 계층만 확인
@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class UserSearchApiTest
@Autowired
constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
) {
    @Test
    @DisplayName("사용자 검색 - 비로그인 사용자도 검색할 수 있고 기본 페이지 크기는 10")
    fun anonymousCanSearch() {
        val user = saveUser("found@test.com", "apisearch-found")

        mockMvc.perform(get("/api/v1/users/search").param("keyword", "apisearch"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-18"))
            .andExpect(jsonPath("$.data.users", hasSize<Any>(1)))
            .andExpect(jsonPath("$.data.users[0].userId").value(checkNotNull(user.id)))
            .andExpect(jsonPath("$.data.users[0].followedByMe").value(false))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(10))
    }

    @Test
    @DisplayName("사용자 검색 - 로그인하면 토큰의 사용자 본인은 결과에서 빠진다")
    fun loggedInSearchExcludesSelf() {
        val viewer = saveUser("viewer@test.com", "apisearch-viewer")
        val other = saveUser("other@test.com", "apisearch-other")

        mockMvc.perform(
            get("/api/v1/users/search")
                .param("keyword", "apisearch")
                .header("Authorization", "Bearer ${jwtProvider.generateAccessToken(viewer.id, viewer.role)}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.users", hasSize<Any>(1)))
            .andExpect(jsonPath("$.data.users[0].userId").value(checkNotNull(other.id)))
    }

    @Test
    @DisplayName("사용자 검색 실패 - 검색어가 공백이면 400-9")
    fun blankKeywordIsRejected() {
        mockMvc.perform(get("/api/v1/users/search").param("keyword", "   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-9"))
    }

    private fun saveUser(email: String, nickname: String): User =
        userRepository.saveAndFlush(User(nickname, email, "encoded"))
}
