package com.gamelog.nbe121423355.global.security.jwt

import com.gamelog.nbe121423355.domain.user.service.TokenReissueService
import com.gamelog.nbe121423355.domain.user.service.TokenReissueService.ReissueResult
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.jsonwebtoken.Claims
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.springframework.http.HttpHeaders.SET_COOKIE
import org.springframework.http.ResponseCookie
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val tokenReissueService: TokenReissueService,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
) : OncePerRequestFilter() {

    // 필터는 spring mvc의 dispatcherservlet보다 앞단에서 실행
    // 그로 인해 globalexceptionhandler 관여 x
    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        servletRequest: HttpServletRequest,
        serveletResponse: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token: String? = resolveToken(servletRequest)
        if (token != null && jwtProvider.validateToken(token)) {
            authenticate(token)
        } else if (token != null) {
            jwtProvider
                .getExpiredAccessTokenClaims(token)
                .ifPresent({ expiredClaims ->
                    tryAutoReissue(servletRequest, serveletResponse, expiredClaims)
                })
        }
        filterChain.doFilter(servletRequest, serveletResponse)
    }

    // accessToken 유효성 검증 후 인증정보 등록
    private fun authenticate(token: String?) {
        try {
            val claims = jwtProvider.parseClaims(token)
            if (!jwtProvider.isAccessToken(claims)) {
                return
            }
            val userId = java.lang.Long.parseLong(claims.getSubject())
            val role = claims.get("role", String::class.java)
            setSecurityContext(userId, role)
        } catch (e: Exception) {}
        // 인증 정보 세팅 실패시 무시(필터는 예외를 던지지 않는다)
    }

    // accessToken이 만료된 경우 refreshToken 쿠키로 조용히 재발급 시도
    // 만료된 accessToken과 refreshToken의 사용자가 같을 때만 재발급
    private fun tryAutoReissue(
        servletRequest: HttpServletRequest,
        response: HttpServletResponse,
        expiredClaims: Claims,
    ) {
        val refreshToken: String? = resolveTokenFromCookie(servletRequest)
        if (refreshToken == null) {
            return
        }
        try {
            val expectedUserId: Long? = java.lang.Long.parseLong(expiredClaims.getSubject())
            val result: ReissueResult = tokenReissueService.reissue(refreshToken, expectedUserId)
            setSecurityContext(result.userId, result.role)
            response.setHeader("New-Access-Token", result.accessToken)
            // Rotation으로 교체된 refreshToken (프록시는 relayAuthHeaders로 Set-Cookie를 브라우저까지 전달)
            val cookie: ResponseCookie =
                refreshTokenCookieFactory.create(result.refreshToken, result.refreshTokenExpiryDate)
            response.addHeader(SET_COOKIE, cookie.toString())
        } catch (e: ServiceException) {} catch (e: Exception) {
            JwtAuthenticationFilter.log.warn("자동 재발급 중 예외 발생", e)
        }
    }

    // SecurityContest에 인증 정보 세팅
    private fun setSecurityContext(userId: Long?, role: String?) {
        val authorities = java.util.List.of(SimpleGrantedAuthority("ROLE_" + role))
        val securityUser = SecurityUser(requireNotNull(userId), authorities)
        val authentication: Authentication =
            UsernamePasswordAuthenticationToken(securityUser, null, securityUser.authorities)
        SecurityContextHolder.getContext().setAuthentication(authentication)
    }

    // Authorization 헤더에서 accessToken 추출
    private fun resolveToken(serveletRequest: HttpServletRequest): String? {
        val header = serveletRequest.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7)
        }
        return null
    }

    // 쿠키에서 refreshToken 추출
    private fun resolveTokenFromCookie(serveletRequest: HttpServletRequest): String? {
        val cookies = serveletRequest.cookies
        if (cookies == null) {
            return null
        }
        for (cookie in cookies) {
            if ("refreshToken".equals(cookie.name)) {
                return cookie.value
            }
        }
        return null
    }

    companion object {
        private val log: org.slf4j.Logger =
            org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }
}
