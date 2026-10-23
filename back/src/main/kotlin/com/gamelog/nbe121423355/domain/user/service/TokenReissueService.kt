package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.user.entity.RefreshToken
import com.gamelog.nbe121423355.domain.user.repository.RefreshTokenRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.global.security.jwt.toDate
import com.gamelog.nbe121423355.global.security.jwt.userId
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// refreshToken으로 accessToken을 재발급하면서 refreshToken도 교체(Rotation)하는 로직
// /refresh API(UserService.refresh)와 자동 재발급 필터(JwtAuthenticationFilter)가 함께 사용
// @Transactional을 클래스에 둔 이유: reissue(String)가 같은 클래스의 reissue(String, Long)을 내부 호출하면
// 프록시를 거치지 않아 메서드에 붙인 @Transactional이 적용되지 않음 (self-invocation)
// noRollbackFor: 재사용 감지 시 세션 삭제 후 예외를 던지는데, 롤백되면 삭제가 취소되므로
// (다른 실패 경우는 DB 변경이 없어서 영향 없음)
@Service
@Transactional(noRollbackFor = [ServiceException::class])
class TokenReissueService(
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    // 재발급 결과 (필터는 userId/role로 SecurityContext를 세팅, 둘 다 refreshToken을 쿠키로 다시 내려줌)
    // refreshTokenExpiryDate: 쿠키 maxAge 계산용 (세션의 절대 만료 시각)
    @JvmRecord
    data class ReissueResult(
        val userId: Long?,
        val role: String,
        val accessToken: String,
        val refreshToken: String,
        val refreshTokenExpiryDate: LocalDateTime,
    )

    // /refresh API용 (비교할 accessToken이 없음)
    fun reissue(refreshToken: String?): ReissueResult {
        return reissue(refreshToken, null)
    }

    // expectedUserId: 자동 재발급 시 만료된 accessToken의 사용자 ID
    // → refreshToken의 사용자와 다르면 재발급 거부 (한 브라우저 두 탭에서 다른 계정 사용 시 세션 섞임 방지)
    // 실패 시 ServiceException(401-2), 재사용 감지 시 ServiceException(401-3)
    fun reissue(refreshToken: String?, expectedUserId: Long?): ReissueResult {
        if (refreshToken == null) {
            throw ServiceException("401-2", "로그인 정보가 없습니다.")
        }
        if (!jwtProvider.validateToken(refreshToken)) {
            throw ServiceException("401-2", "유효하지 않은 토큰 입니다.")
        }
        val claims = jwtProvider.parseClaims(refreshToken)
        if (!jwtProvider.isRefreshToken(claims)) {
            throw ServiceException("401-2", "유효하지 않은 토큰 입니다.")
        }
        // DB 조회 전에 먼저 비교 (불일치면 쿼리 없이 종료)
        if (expectedUserId != null && expectedUserId != claims.userId) {
            throw ServiceException("401-2", "토큰의 사용자 정보가 일치하지 않습니다.")
        }
        val familyId = jwtProvider.getFamilyId(claims)
        if (familyId == null) {
            throw ServiceException("401-2", "유효하지 않은 토큰 입니다.")
        }
        val jti = claims.id
        val now = LocalDateTime.now()
        // 교체 시도를 조회보다 먼저 실행
        // MySQL(REPEATABLE READ)은 트랜잭션의 첫 조회 시점에 스냅샷이 고정됨
        // → 먼저 조회하면 동시에 들어온 다른 요청의 교체 결과가 안 보여서 유예/재사용 판단이 틀어짐
        val rotated =
            refreshTokenRepository.rotate(familyId, jti, UUID.randomUUID().toString(), now) == 1
        val session =
            refreshTokenRepository
                .findByFamilyIdWithUser(familyId)
                .orElseThrow { ServiceException("401-2", "로그아웃된 토큰입니다.") }
        val user = session.user
        // 탈퇴 시 세션은 전부 삭제되지만 방어적으로 한 번 더 확인
        if (user.isDeleted) {
            throw ServiceException("401-2", "로그인 정보가 없습니다.")
        }
        if (!rotated && !isWithinGracePeriod(session, jti, now)) {
            // 이미 교체된 refreshToken이 유예 시간 이후에 다시 사용됨 → 탈취 가능성, 세션 폐기
            refreshTokenRepository.deleteByFamilyId(familyId)
            log.warn(
                "refreshToken 재사용 감지 - 세션 폐기 userId={}, familyId={}",
                user.id,
                familyId,
            )
            throw ServiceException("401-3", "만료된 로그인 정보입니다. 다시 로그인해 주세요.")
        }
        // 교체 성공: currentJti = 방금 만든 jti
        // 유예 시간 내 요청: currentJti = 다른 요청이 교체한 jti → 같은 refreshToken을 다시 내려줘서 쿠키 복구
        val expiration = session.expiryDate.toDate()
        val newRefreshToken =
            jwtProvider.generateRefreshToken(user.id, familyId, session.currentJti, expiration)
        val newAccessToken = jwtProvider.generateAccessToken(user.id, user.role)
        return ReissueResult(
            user.id,
            user.role,
            newAccessToken,
            newRefreshToken,
            session.expiryDate,
        )
    }

    // 직전 refreshToken이고 교체된 지 유예 시간이 안 지났으면 동시 요청으로 판단
    private fun isWithinGracePeriod(
        session: RefreshToken,
        jti: String,
        now: LocalDateTime,
    ): Boolean {
        return jti == session.prevJti &&
            session.rotatedAt?.isAfter(now.minus(GRACE_PERIOD)) == true
    }

    companion object {
        private val log = LoggerFactory.getLogger(TokenReissueService::class.java)

        // 교체 직후 이전 refreshToken을 허용하는 시간 (동시 요청 / 응답 유실 대비)
        private val GRACE_PERIOD: Duration = Duration.ofSeconds(10)
    }
}
