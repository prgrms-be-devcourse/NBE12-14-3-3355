package com.gamelog.nbe121423355.global.security.jwt

import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.time.Duration.ZERO
import java.time.LocalDateTime
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

// refreshToken 쿠키 생성 (로그인, 로그아웃, /refresh, 자동 재발급 필터에서 공통 사용)
@Component
class RefreshTokenCookieFactory(private val cookieProperties: CookieProperties) {
    // maxAge = 세션 만료까지 남은 시간 (Rotation돼도 로그인 시점 기준 절대 만료라 점점 줄어듦)
    // expiryDate는 ZoneId.systemDefault() 기준으로 저장되므로 비교도 LocalDateTime.now()
    fun create(refreshToken: String?, expiryDate: LocalDateTime?): ResponseCookie {
        val maxAge = Duration.between(LocalDateTime.now(), expiryDate)
        return build(refreshToken, if (maxAge.isNegative) Duration.ZERO else maxAge)
    }

    // 쿠키 삭제용 (로그아웃)
    fun delete(): ResponseCookie {
        return build("", Duration.ZERO)
    }

    private fun build(value: String?, maxAge: Duration): ResponseCookie {
        return ResponseCookie.from(RefreshTokenCookieFactory.COOKIE_NAME, value)
            .httpOnly(true)
            .path("/")
            .maxAge(maxAge)
            .sameSite(cookieProperties.sameSite)
            .secure(cookieProperties.secure)
            .build()
    }

    companion object {
        const val COOKIE_NAME = "refreshToken"
    }
}

// ResponseCookie는 서블릿 Cookie가 아니라서 addCookie 대신 Set-Cookie 헤더로 내려야 함
fun HttpServletResponse.addResponseCookie(cookie: ResponseCookie) {
    addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
}
