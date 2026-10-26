package com.gamelog.nbe121423355.global.security.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class JwtProviderTest {
    private lateinit var jwtProvider: JwtProvider
    private lateinit var secretKey: SecretKey

    @BeforeEach
    fun setUp() {
        jwtProvider = JwtProvider(JwtProperties(SECRET, 3600000L, 1209600000L))
        secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))
    }

    @Test
    @DisplayName("getExpiredAccessTokenClaims - 아직 만료되지 않은 accessToken이면 null")
    fun getExpiredAccessTokenClaims_null_whenNotExpired() {
        val token = jwtProvider.generateAccessToken(1L, "USER")

        assertThat(jwtProvider.getExpiredAccessTokenClaims(token)).isNull()
    }

    @Test
    @DisplayName("getExpiredAccessTokenClaims - 시간이 지나 만료된 accessToken이면 클레임 반환 (사용자 ID 포함)")
    fun getExpiredAccessTokenClaims_present_whenExpired() {
        val expiredToken = createToken(secretKey, "Access", expiredIssuedAt(), expiredExpiration())

        assertThat(jwtProvider.getExpiredAccessTokenClaims(expiredToken)?.subject).isEqualTo("1")
    }

    @Test
    @DisplayName("getExpiredAccessTokenClaims - 만료된 refreshToken이면 null (refreshToken으로 재발급 경로 진입 차단)")
    fun getExpiredAccessTokenClaims_null_whenExpiredRefreshToken() {
        val expiredToken = createToken(secretKey, "Refresh", expiredIssuedAt(), expiredExpiration())

        assertThat(jwtProvider.getExpiredAccessTokenClaims(expiredToken)).isNull()
    }

    @Test
    @DisplayName("getExpiredAccessTokenClaims - token_type이 없는 기존 토큰이 만료되면 null")
    fun getExpiredAccessTokenClaims_null_whenNoTokenType() {
        val expiredToken = createToken(secretKey, null, expiredIssuedAt(), expiredExpiration())

        assertThat(jwtProvider.getExpiredAccessTokenClaims(expiredToken)).isNull()
    }

    @Test
    @DisplayName("getExpiredAccessTokenClaims - 서명이 위조된 토큰이면 null (만료가 아니라 무효라서 재발급 대상 아님)")
    fun getExpiredAccessTokenClaims_null_whenTampered() {
        val wrongKey = Jwts.SIG.HS256.key().build()
        val tamperedToken = createToken(wrongKey, "Access", Date(), Date(System.currentTimeMillis() + 1000 * 60 * 60))

        assertThat(jwtProvider.getExpiredAccessTokenClaims(tamperedToken)).isNull()
    }

    @Test
    @DisplayName("isAccessToken / isRefreshToken - 생성 메서드에 따라 토큰 종류가 구분됨")
    fun tokenType_distinguished() {
        val accessToken = jwtProvider.generateAccessToken(1L, "USER")
        val refreshToken =
            jwtProvider.generateRefreshToken(
                1L,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                jwtProvider.newRefreshTokenExpiration(),
            )

        assertThat(jwtProvider.isAccessToken(jwtProvider.parseClaims(accessToken))).isTrue()
        assertThat(jwtProvider.isRefreshToken(jwtProvider.parseClaims(accessToken))).isFalse()
        assertThat(jwtProvider.isRefreshToken(jwtProvider.parseClaims(refreshToken))).isTrue()
        assertThat(jwtProvider.isAccessToken(jwtProvider.parseClaims(refreshToken))).isFalse()
    }

    @Test
    @DisplayName("isAccessToken / isRefreshToken - token_type이 없는 기존 토큰은 둘 다 false")
    fun tokenType_noTokenType() {
        val token = createToken(secretKey, null, Date(), Date(System.currentTimeMillis() + 1000 * 60 * 60))

        assertThat(jwtProvider.isAccessToken(jwtProvider.parseClaims(token))).isFalse()
        assertThat(jwtProvider.isRefreshToken(jwtProvider.parseClaims(token))).isFalse()
    }

    private fun expiredIssuedAt() = Date(System.currentTimeMillis() - 1000 * 60 * 60) // 1시간 전 발급

    private fun expiredExpiration() = Date(System.currentTimeMillis() - 1000 * 60) // 1분 전 만료

    // tokenType이 null이면 token_type 클레임 없이 생성 (배포 전 발급된 토큰 재현)
    private fun createToken(key: SecretKey, tokenType: String?, issuedAt: Date, expiration: Date): String {
        val builder =
            Jwts.builder()
                .subject("1")
                .claim("role", "USER")
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
        if (tokenType != null) {
            builder.claim("token_type", tokenType)
        }
        return builder.compact()
    }

    companion object {
        private const val SECRET = "2yFvv+amd1bJKWDQ/picSaxbhQ+VKrMIjFqIqbBqib8="
    }
}
