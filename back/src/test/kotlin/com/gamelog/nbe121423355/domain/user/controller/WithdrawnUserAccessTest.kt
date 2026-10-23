package com.gamelog.nbe121423355.domain.user.controller

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

// 탈퇴해도 이미 발급된 accessToken은 만료 전까지 서명 검증을 통과하므로, 서비스에서 탈퇴 여부로 막는지 확인
@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class WithdrawnUserAccessTest
@Autowired
constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
) {
    private lateinit var user: User
    private lateinit var accessToken: String

    @BeforeEach
    fun withdraw() {
        user = userRepository.saveAndFlush(User("탈퇴자", "withdrawn@test.com", passwordEncoder.encode(PASSWORD)))
        accessToken = jwtProvider.generateAccessToken(user.id, user.role)

        mockMvc.perform(
            delete("/api/v1/users/me")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password": "$PASSWORD"}"""),
        )
            .andExpect(status().isOk)
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("myEndpoints")
    @DisplayName("탈퇴 후 만료 전 accessToken으로 내 정보 API를 호출하면 404-1")
    fun withdrawnUserCannotUseMyEndpoints(method: HttpMethod, path: String, body: String?) {
        val builder = request(method, path).header("Authorization", "Bearer $accessToken")
        body?.let { builder.contentType(MediaType.APPLICATION_JSON).content(it) }

        mockMvc.perform(builder)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("myEndpoints")
    @DisplayName("탈퇴 후 요청이 거부돼도 익명화된 정보는 그대로 유지된다")
    fun anonymizedDataIsKept(method: HttpMethod, path: String, body: String?) {
        val builder = request(method, path).header("Authorization", "Bearer $accessToken")
        body?.let { builder.contentType(MediaType.APPLICATION_JSON).content(it) }

        mockMvc.perform(builder)

        val saved = userRepository.findByIdOrNull(user.id!!)!!
        assertThat(saved.nickname).isEqualTo("탈퇴한사용자_${user.id}")
        assertThat(saved.email).isEqualTo("deleted_${user.id}@deleted.local")
        assertThat(saved.isOnboardingCompleted).isFalse()
    }

    companion object {
        private const val PASSWORD = "password123"

        @JvmStatic
        fun myEndpoints(): List<Arguments> =
            listOf(
                Arguments.of(HttpMethod.GET, "/api/v1/users/me", null),
                Arguments.of(
                    HttpMethod.PATCH,
                    "/api/v1/users/me",
                    """{"nickname": "되살린닉네임", "profileImageUrl": null, "bio": null}""",
                ),
                Arguments.of(HttpMethod.PATCH, "/api/v1/users/me/onboarding", null),
                Arguments.of(HttpMethod.PATCH, "/api/v1/users/me/onboarding/skip", null),
                Arguments.of(
                    HttpMethod.PATCH,
                    "/api/v1/users/me/password",
                    """{"currentPassword": "$PASSWORD", "newPassword": "newPassword123"}""",
                ),
                Arguments.of(HttpMethod.PUT, "/api/v1/users/me/preferred-genres", """{"genreIds": [1]}"""),
                Arguments.of(HttpMethod.PUT, "/api/v1/users/me/preferred-games", """{"gameIds": [1]}"""),
            )
    }
}
