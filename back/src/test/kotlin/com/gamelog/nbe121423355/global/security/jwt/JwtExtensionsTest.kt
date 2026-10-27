package com.gamelog.nbe121423355.global.security.jwt

import io.jsonwebtoken.Jwts
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class JwtExtensionsTest {
    private val jwtProvider =
        JwtProvider(JwtProperties(SECRET, 3_600_000L, 1_209_600_000L))

    @Test
    @DisplayName("Claims.userId - subject 문자열을 Long으로 꺼낸다")
    fun userIdReadsSubject() {
        val claims = Jwts.claims().subject("42").build()

        assertThat(claims.userId).isEqualTo(42L)
    }

    @Test
    @DisplayName("Claims.userId - 발급한 accessToken에서 넣은 userId를 그대로 꺼낸다")
    fun userIdRoundTripsThroughAccessToken() {
        val token = jwtProvider.generateAccessToken(7L, "USER")

        assertThat(jwtProvider.parseClaims(token).userId).isEqualTo(7L)
    }

    @Test
    @DisplayName("Claims.userId - subject가 숫자가 아니면 NumberFormatException")
    fun userIdThrowsWhenSubjectIsNotNumber() {
        val claims = Jwts.claims().subject("not-a-number").build()

        assertThatThrownBy { claims.userId }.isInstanceOf(NumberFormatException::class.java)
    }

    @Test
    @DisplayName("Date.toLocalDateTime - 시스템 시간대 기준으로 변환한다")
    fun dateToLocalDateTimeUsesSystemZone() {
        val result = Date(0L).toLocalDateTime()

        assertThat(result).isEqualTo(LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()))
    }

    @Test
    @DisplayName("LocalDateTime.toDate → toLocalDateTime - 밀리초 단위까지 원래 값으로 돌아온다")
    fun localDateTimeRoundTripsToMillis() {
        val expiry = LocalDateTime.of(2026, 10, 23, 12, 30, 15, 123_456_789)

        val result = expiry.toDate().toLocalDateTime()

        // Date는 밀리초까지만 담으므로 그 아래는 버려짐
        assertThat(result).isEqualTo(expiry.truncatedTo(ChronoUnit.MILLIS))
    }

    @Test
    @DisplayName("Date.toLocalDateTime → toDate - 같은 시각의 Date로 돌아온다")
    fun dateRoundTrips() {
        val expiration = jwtProvider.newRefreshTokenExpiration()

        assertThat(expiration.toLocalDateTime().toDate()).isEqualTo(expiration)
    }

    companion object {
        private const val SECRET = "2yFvv+amd1bJKWDQ/picSaxbhQ+VKrMIjFqIqbBqib8="
    }
}
