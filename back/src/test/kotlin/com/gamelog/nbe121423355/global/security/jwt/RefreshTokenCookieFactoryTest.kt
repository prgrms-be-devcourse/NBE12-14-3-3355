package com.gamelog.nbe121423355.global.security.jwt

import java.time.Duration
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletResponse

class RefreshTokenCookieFactoryTest {
    private val factory = RefreshTokenCookieFactory(CookieProperties("Lax", false))

    @Test
    @DisplayName("create - 토큰 값과 보안 속성(HttpOnly, Path=/)을 담는다")
    fun createSetsValueAndAttributes() {
        val cookie = factory.create("refresh-token", LocalDateTime.now().plusHours(1))

        assertThat(cookie.name).isEqualTo(RefreshTokenCookieFactory.COOKIE_NAME)
        assertThat(cookie.value).isEqualTo("refresh-token")
        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.path).isEqualTo("/")
    }

    @Test
    @DisplayName("create - maxAge는 세션 만료까지 남은 시간")
    fun createMaxAgeIsRemainingTime() {
        val cookie = factory.create("refresh-token", LocalDateTime.now().plusHours(1))

        assertThat(cookie.maxAge).isBetween(Duration.ofMinutes(59), Duration.ofHours(1))
    }

    @Test
    @DisplayName("create - 이미 만료된 시각이면 maxAge는 음수가 아니라 0")
    fun createMaxAgeIsZeroWhenExpired() {
        val cookie = factory.create("refresh-token", LocalDateTime.now().minusMinutes(1))

        assertThat(cookie.maxAge).isEqualTo(Duration.ZERO)
    }

    @ParameterizedTest
    @CsvSource("Lax, false", "None, true")
    @DisplayName("create - SameSite와 Secure는 환경별 설정값을 따른다")
    fun createFollowsCookieProperties(sameSite: String, secure: Boolean) {
        val factory = RefreshTokenCookieFactory(CookieProperties(sameSite, secure))

        val cookie = factory.create("refresh-token", LocalDateTime.now().plusHours(1))

        assertThat(cookie.sameSite).isEqualTo(sameSite)
        assertThat(cookie.isSecure).isEqualTo(secure)
    }

    @Test
    @DisplayName("delete - 빈 값에 maxAge 0인 쿠키로 브라우저 쿠키를 지운다")
    fun deleteExpiresCookie() {
        val cookie = factory.delete()

        assertThat(cookie.name).isEqualTo(RefreshTokenCookieFactory.COOKIE_NAME)
        assertThat(cookie.value).isEmpty()
        assertThat(cookie.maxAge).isEqualTo(Duration.ZERO)
        assertThat(cookie.path).isEqualTo("/")
    }

    @Test
    @DisplayName("addResponseCookie - 쿠키를 Set-Cookie 헤더로 내린다")
    fun addResponseCookieWritesSetCookieHeader() {
        val response = MockHttpServletResponse()

        response.addResponseCookie(factory.create("refresh-token", LocalDateTime.now().plusHours(1)))

        // MockHttpServletResponse가 Expires 날짜 형식을 바꿔 저장하므로 문자열 대신 파싱된 쿠키로 비교
        val cookie = response.getCookie(RefreshTokenCookieFactory.COOKIE_NAME)
        assertThat(cookie).isNotNull
        assertThat(checkNotNull(cookie).value).isEqualTo("refresh-token")
        assertThat(cookie.isHttpOnly).isTrue()
    }

    @Test
    @DisplayName("addResponseCookie - 기존 Set-Cookie 헤더를 덮어쓰지 않고 추가한다")
    fun addResponseCookieKeepsExistingHeaders() {
        val response = MockHttpServletResponse()
        response.addHeader(HttpHeaders.SET_COOKIE, "other=value")

        response.addResponseCookie(factory.delete())

        val headers = response.getHeaders(HttpHeaders.SET_COOKIE)
        assertThat(headers).hasSize(2)
        assertThat(headers[0]).isEqualTo("other=value")
        assertThat(headers[1]).startsWith("refreshToken=;").contains("Max-Age=0")
    }
}
