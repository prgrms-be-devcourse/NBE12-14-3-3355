package com.gamelog.nbe121423355.global.security.jwt

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.Cookie
import java.util.Date
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class JwtAuthenticationFilterTest
@Autowired
constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProperties: JwtProperties,
    private val jwtProvider: JwtProvider,
) {
    private fun saveUser(email: String, nickname: String, rawPassword: String): User =
        userRepository.save(User(nickname, email, checkNotNull(passwordEncoder.encode(rawPassword))))

    private fun loginAndGetRefreshTokenCookie(): Cookie {
        val loginResult =
            mockMvc.perform(
                post("/api/v1/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email": "autorefresh-test@test.com", "password": "password123"}"""),
            )
                .andExpect(status().isOk)
                .andReturn()
        val refreshTokenCookie = loginResult.response.getCookie("refreshToken")
        assertThat(refreshTokenCookie).isNotNull
        return refreshTokenCookie!!
    }

    // 시간이 지나 만료된(서명은 정상) accessToken을 직접 생성
    private fun expiredAccessToken(userId: Long?, role: String): String {
        val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret))
        return Jwts.builder()
            .subject(userId.toString())
            .claim("token_type", "Access")
            .claim("role", role)
            .issuedAt(Date(System.currentTimeMillis() - 1000 * 60 * 60))
            .expiration(Date(System.currentTimeMillis() - 1000 * 60))
            .signWith(key)
            .compact()
    }

    // 시간이 지나 만료된(서명은 정상) refreshToken을 직접 생성
    private fun expiredRefreshToken(userId: Long?): String {
        val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret))
        return Jwts.builder()
            .subject(userId.toString())
            .id("expired-refresh-jti")
            .claim("token_type", "Refresh")
            .issuedAt(Date(System.currentTimeMillis() - 1000 * 60 * 60))
            .expiration(Date(System.currentTimeMillis() - 1000 * 60))
            .signWith(key)
            .compact()
    }

    // 서명이 위조된(만료는 아닌) accessToken을 직접 생성
    private fun tamperedAccessToken(userId: Long?, role: String): String {
        val wrongKey = Jwts.SIG.HS256.key().build()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 1000 * 60 * 60))
            .signWith(wrongKey)
            .compact()
    }

    @Test
    @DisplayName("accessToken 만료 + 유효한 refreshToken 쿠키 → 자동 재발급 성공, New-Access-Token 헤더 포함")
    fun autoReissue_success() {
        val user = saveUser("autorefresh-test@test.com", "nickname", "password123")
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        val result =
            mockMvc.perform(
                get("/api/v1/users/me")
                    .header("Authorization", "Bearer ${expiredAccessToken(user.id, user.role)}")
                    .cookie(refreshTokenCookie),
            )
                .andExpect(status().isOk)
                .andExpect(header().exists("New-Access-Token"))
                .andReturn()

        // Rotation: 자동 재발급 시에도 교체된 refreshToken이 Set-Cookie로 내려옴
        val rotatedCookie = result.response.getCookie("refreshToken")
        assertThat(rotatedCookie).isNotNull
        assertThat(rotatedCookie!!.value).isNotEqualTo(refreshTokenCookie.value)
    }

    @Test
    @DisplayName("accessToken 만료 + refreshToken 쿠키 없음 → 재발급 안 되고 401")
    fun autoReissue_fail_noRefreshCookie() {
        val user = saveUser("autorefresh-test@test.com", "nickname", "password123")

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer ${expiredAccessToken(user.id, user.role)}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().doesNotExist("New-Access-Token"))
    }

    @Test
    @DisplayName("accessToken 만료 + refreshToken이 DB에 없음(로그인 거치지 않고 직접 생성한 토큰) → 재발급 안 되고 401")
    fun autoReissue_fail_refreshTokenNotInDb() {
        val user = saveUser("autorefresh-test@test.com", "nickname", "password123")
        val refreshToken =
            jwtProvider.generateRefreshToken(
                user.id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                jwtProvider.newRefreshTokenExpiration(),
            )

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer ${expiredAccessToken(user.id, user.role)}")
                .cookie(Cookie("refreshToken", refreshToken)),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().doesNotExist("New-Access-Token"))
    }

    @Test
    @DisplayName("accessToken 서명 위조(만료 아님) + 유효한 refreshToken 쿠키 → 재발급 시도 자체를 안 하고 401")
    fun autoReissue_fail_tamperedAccessToken() {
        val user = saveUser("autorefresh-test@test.com", "nickname", "password123")
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer ${tamperedAccessToken(user.id, user.role)}")
                .cookie(refreshTokenCookie),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().doesNotExist("New-Access-Token"))
    }

    @Test
    @DisplayName("유효한 refreshToken을 Authorization 헤더에 넣으면 → 인증되지 않고 401")
    fun authenticate_fail_refreshTokenAsBearer() {
        saveUser("autorefresh-test@test.com", "nickname", "password123")
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer ${refreshTokenCookie.value}"),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("만료된 refreshToken을 Authorization 헤더에 넣으면 + 유효한 refreshToken 쿠키 → 재발급 시도 자체를 안 하고 401")
    fun autoReissue_fail_expiredRefreshTokenAsBearer() {
        val user = saveUser("autorefresh-test@test.com", "nickname", "password123")
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer ${expiredRefreshToken(user.id)}")
                .cookie(refreshTokenCookie),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().doesNotExist("New-Access-Token"))
    }

    @Test
    @DisplayName("A의 만료된 accessToken + B의 refreshToken 쿠키(두 탭 다른 계정) → B 토큰으로 재발급되지 않고 401")
    fun autoReissue_fail_userIdMismatch() {
        val userA = saveUser("other-user@test.com", "userA", "password123")
        saveUser("autorefresh-test@test.com", "userB", "password123")
        val refreshTokenCookieOfB = loginAndGetRefreshTokenCookie()

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer ${expiredAccessToken(userA.id, userA.role)}")
                .cookie(refreshTokenCookieOfB),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().doesNotExist("New-Access-Token"))
    }

    @Test
    @DisplayName("accessToken 만료 + refreshToken 쿠키에 accessToken이 들어있음 → 재발급 안 되고 401")
    fun autoReissue_fail_accessTokenInRefreshCookie() {
        val user = saveUser("autorefresh-test@test.com", "nickname", "password123")
        val accessToken = jwtProvider.generateAccessToken(user.id, user.role)

        mockMvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer ${expiredAccessToken(user.id, user.role)}")
                .cookie(Cookie("refreshToken", accessToken)),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().doesNotExist("New-Access-Token"))
    }
}
