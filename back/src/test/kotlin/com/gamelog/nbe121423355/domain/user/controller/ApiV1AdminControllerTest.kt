package com.gamelog.nbe121423355.domain.user.controller

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class ApiV1AdminControllerTest
@Autowired
constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider,
) {
    @Test
    @DisplayName("관리자 승격 - 인증 없이 호출하면 401")
    fun promoteFailsWithoutAuthentication() {
        val target = saveUser("target@test.com", "대상")

        mockMvc.perform(patch("/api/v1/admin/users/{userId}/role", target.id))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-1"))
    }

    @Test
    @DisplayName("관리자 승격 - USER 권한이면 403이고 대상 권한은 그대로")
    fun promoteFailsForUserRole() {
        val user = saveUser("user@test.com", "일반")
        val target = saveUser("target@test.com", "대상")

        mockMvc.perform(
            patch("/api/v1/admin/users/{userId}/role", target.id)
                .header("Authorization", "Bearer ${tokenFor(user)}"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))

        assertThat(userRepository.findByIdOrNull(target.id!!)!!.role).isEqualTo("USER")
    }

    @Test
    @DisplayName("관리자 승격 성공 - ADMIN 권한이면 대상이 ADMIN이 된다")
    fun promoteSucceedsForAdminRole() {
        val admin = saveAdmin()
        val target = saveUser("target@test.com", "대상")

        mockMvc.perform(
            patch("/api/v1/admin/users/{userId}/role", target.id)
                .header("Authorization", "Bearer ${tokenFor(admin)}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-13"))
            .andExpect(jsonPath("$.data.id").value(target.id!!))
            .andExpect(jsonPath("$.data.role").value("ADMIN"))

        assertThat(userRepository.findByIdOrNull(target.id!!)!!.role).isEqualTo("ADMIN")
    }

    @Test
    @DisplayName("관리자 승격 실패 - 존재하지 않는 사용자면 404")
    fun promoteFailsForMissingUser() {
        val admin = saveAdmin()

        mockMvc.perform(
            patch("/api/v1/admin/users/{userId}/role", Long.MAX_VALUE)
                .header("Authorization", "Bearer ${tokenFor(admin)}"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    @Test
    @DisplayName("관리자 승격 실패 - 탈퇴한 사용자는 존재하지 않는 사용자로 보고 404")
    fun promoteFailsForWithdrawnUser() {
        val admin = saveAdmin()
        val withdrawn = saveUser("withdrawn@test.com", "탈퇴예정")
        withdrawn.withdraw("random-encoded")
        userRepository.saveAndFlush(withdrawn)

        mockMvc.perform(
            patch("/api/v1/admin/users/{userId}/role", withdrawn.id)
                .header("Authorization", "Bearer ${tokenFor(admin)}"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    private fun saveUser(email: String, nickname: String): User =
        userRepository.saveAndFlush(User(nickname, email, "encoded"))

    private fun saveAdmin(): User =
        saveUser("admin@test.com", "관리자").also {
            it.promoteToAdmin()
            userRepository.saveAndFlush(it)
        }

    private fun tokenFor(user: User): String = jwtProvider.generateAccessToken(user.id, user.role)
}
