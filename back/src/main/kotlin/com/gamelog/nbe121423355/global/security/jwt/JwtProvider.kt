package com.gamelog.nbe121423355.global.security.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.Decoders.BASE64
import io.jsonwebtoken.security.Keys
import java.util.Date
import java.util.Optional
import javax.crypto.SecretKey
import org.springframework.stereotype.Component

@Component
class JwtProvider(private val jwtProperties: JwtProperties) {
    private val secretKey: SecretKey?

    // 토큰 제공 클래스
    init {
        val keyBytes = Decoders.BASE64.decode(jwtProperties.secret)
        this.secretKey = Keys.hmacShaKeyFor(keyBytes)
    }

    // accesstoken 생성 메소드
    fun generateAccessToken(userId: Long?, role: String?): String {
        val now = Date()
        val expiration = Date(now.time + jwtProperties.accessTokenValidity)

        return Jwts.builder()
            .subject(userId.toString())
            .claim(TOKEN_TYPE, ACCESS)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(secretKey)
            .compact()
    }

    // refreshtoken 생성 메소드
    // jti/만료시각을 밖에서 받는 이유: 유예 시간 내 재요청 시 같은 jti·같은 만료시각으로 다시 서명해야 함
    fun generateRefreshToken(
        userId: Long?,
        familyId: String?,
        jti: String?,
        expiration: Date?,
    ): String {
        return Jwts.builder()
            .subject(userId.toString())
            .id(jti)
            .claim(TOKEN_TYPE, REFRESH)
            .claim(FAMILY_ID, familyId)
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(secretKey)
            .compact()
    }

    // 새 로그인 세션의 만료 시각 (지금 + refreshToken 유효기간)
    fun newRefreshTokenExpiration(): Date {
        return Date(System.currentTimeMillis() + jwtProperties.refreshTokenValidity)
    }

    // refreshToken의 세션 식별자 (fid가 없는 기존 토큰은 null)
    fun getFamilyId(claims: Claims): String? {
        return claims.get(FAMILY_ID, String::class.java)
    }

    // 클레임에서 userId 추출
    fun getUserId(token: String?): Long {
        return parseClaims(token).subject.toLong()
    }

    // 토큰의 클레임 전체 파싱 (서명 검증 포함)
    fun parseClaims(token: String?): Claims {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
    }

    // 서명/만료 검증
    fun validateToken(token: String?): Boolean {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build() // 시크릿키로 서명 검증
                .parseSignedClaims(token) // 토큰을 파싱하면서 서명검증까지 수행, 서명이 안맞거나 만료되었으면 여기서 예외를 던지기
            return true
        } catch (e: JwtException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
    }

    // 서명은 유효하지만 시간이 지나 만료된 accessToken이면 그 클레임을 반환 (자동 재발급 대상 여부 + 사용자 ID 비교용)
    // 만료되지 않았거나, accessToken이 아니거나, 서명이 위조된 토큰이면 empty
    fun getExpiredAccessTokenClaims(token: String?): Optional<Claims> {
        try {
            parseClaims(token)
            return Optional.empty()
        } catch (e: ExpiredJwtException) {
            return if (isAccessToken(e.claims)) Optional.of(e.claims) else Optional.empty()
        } catch (e: JwtException) {
            return Optional.empty()
        } catch (e: IllegalArgumentException) {
            return Optional.empty()
        }
    }

    // 토큰 종류 판별 (token_type 클레임이 없는 기존 토큰은 false)
    fun isAccessToken(claims: Claims): Boolean {
        return claims.get(TOKEN_TYPE, String::class.java) == ACCESS
    }

    fun isRefreshToken(claims: Claims): Boolean {
        return claims.get(TOKEN_TYPE, String::class.java) == REFRESH
    }

    companion object {
        // 토큰 종류 구분용 클레임
        private const val TOKEN_TYPE = "token_type"
        private const val ACCESS = "Access"
        private const val REFRESH = "Refresh"

        // refreshToken의 로그인 세션(family) 식별 클레임
        private const val FAMILY_ID = "fid"
    }
}
