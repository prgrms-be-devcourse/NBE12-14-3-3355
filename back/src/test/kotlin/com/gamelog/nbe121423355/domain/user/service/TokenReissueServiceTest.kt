package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.user.dto.LoginRequestDto
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.RefreshTokenRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.security.jwt.JwtProperties
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import jakarta.persistence.EntityManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class TokenReissueServiceTest
@Autowired
constructor(
    private val tokenReissueService: TokenReissueService,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val jwtProperties: JwtProperties,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val entityManager: EntityManager,
) {
    private fun saveUser(email: String, nickname: String): User {
        return userRepository.save(User(nickname, email, checkNotNull(passwordEncoder.encode("password123"))))
    }

    private fun loginAndGetRefreshToken(email: String): String {
        return userService.login(LoginRequestDto(email, "password123")).refreshToken
    }

    @Test
    @DisplayName("재발급 성공 - expectedUserId가 refreshToken의 사용자와 같으면 해당 사용자의 accessToken 발급")
    fun reissue_success_userIdMatch() {
        val user = saveUser("test@test.com", "nickname")
        val refreshToken = loginAndGetRefreshToken("test@test.com")

        val result = tokenReissueService.reissue(refreshToken, user.id)

        assertThat(result.userId).isEqualTo(user.id)
        assertThat(result.role).isEqualTo(user.role)
        assertThat(jwtProvider.getUserId(result.accessToken)).isEqualTo(user.id)
    }

    @Test
    @DisplayName("재발급 성공 - expectedUserId 없이(/refresh API) 호출하면 비교 없이 발급")
    fun reissue_success_withoutExpectedUserId() {
        val user = saveUser("test@test.com", "nickname")
        val refreshToken = loginAndGetRefreshToken("test@test.com")

        val result = tokenReissueService.reissue(refreshToken)

        assertThat(result.userId).isEqualTo(user.id)
    }

    @Test
    @DisplayName("재발급 실패 - expectedUserId가 refreshToken의 사용자와 다르면 401-2")
    fun reissue_fail_userIdMismatch() {
        val userA = saveUser("a@test.com", "userA")
        saveUser("b@test.com", "userB")
        val refreshTokenOfB = loginAndGetRefreshToken("b@test.com")

        assertThatThrownBy { tokenReissueService.reissue(refreshTokenOfB, userA.id) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-2") })
    }

    // ===== Rotation =====
    // H2는 MySQL(REPEATABLE READ)의 스냅샷·잠금 동작과 UPDATE SET 적용 순서를 그대로 재현하지 못하므로
    // 여기서는 교체/유예/재사용 분기 로직만 검증 (동시 요청 경쟁은 MySQL 환경에서 별도 확인 필요)

    private fun jtiOf(refreshToken: String): String {
        return jwtProvider.parseClaims(refreshToken).id
    }

    private fun familyIdOf(refreshToken: String): String? {
        return jwtProvider.getFamilyId(jwtProvider.parseClaims(refreshToken))
    }

    // 마지막 교체 시각을 과거로 돌려 유예 시간이 지난 상황을 만든다
    private fun moveRotatedAtBack(familyId: String?, duration: Duration) {
        entityManager.createQuery("update RefreshToken r set r.rotatedAt = :rotatedAt where r.familyId = :familyId")
            .setParameter("rotatedAt", LocalDateTime.now().minus(duration))
            .setParameter("familyId", familyId)
            .executeUpdate()
        entityManager.clear()
    }

    private fun assertResultCode(e: Throwable, resultCode: String) {
        assertThat(e).isInstanceOf(ServiceException::class.java)
        assertThat((e as ServiceException).resultCode).isEqualTo(resultCode)
    }

    @Test
    @DisplayName("Rotation - 재발급하면 같은 세션(fid)에 새 jti의 refreshToken이 발급되고, DB의 current/prev jti가 교체됨")
    fun rotation_issuesNewRefreshToken() {
        saveUser("test@test.com", "nickname")
        val oldRefreshToken = loginAndGetRefreshToken("test@test.com")

        val result = tokenReissueService.reissue(oldRefreshToken)

        assertThat(familyIdOf(result.refreshToken)).isEqualTo(familyIdOf(oldRefreshToken))
        assertThat(jtiOf(result.refreshToken)).isNotEqualTo(jtiOf(oldRefreshToken))
        val session = checkNotNull(refreshTokenRepository.findByFamilyId(familyIdOf(oldRefreshToken)))
        assertThat(session.currentJti).isEqualTo(jtiOf(result.refreshToken))
        assertThat(session.prevJti).isEqualTo(jtiOf(oldRefreshToken))
        assertThat(session.rotatedAt).isNotNull()
    }

    @Test
    @DisplayName("Rotation - 교체돼도 세션 만료 시각(로그인 기준 절대 만료)은 그대로 유지")
    fun rotation_keepsAbsoluteExpiry() {
        saveUser("test@test.com", "nickname")
        val oldRefreshToken = loginAndGetRefreshToken("test@test.com")
        val oldExpiration = jwtProvider.parseClaims(oldRefreshToken).expiration

        val result = tokenReissueService.reissue(oldRefreshToken)

        assertThat(jwtProvider.parseClaims(result.refreshToken).expiration).isEqualTo(oldExpiration)
        assertThat(result.refreshTokenExpiryDate)
            .isEqualTo(checkNotNull(refreshTokenRepository.findByFamilyId(familyIdOf(oldRefreshToken))).expiryDate)
    }

    @Test
    @DisplayName("Rotation - 새로 받은 refreshToken으로 다시 재발급 가능 (연속 교체)")
    fun rotation_newRefreshTokenWorks() {
        saveUser("test@test.com", "nickname")
        val refreshToken1 = loginAndGetRefreshToken("test@test.com")
        val refreshToken2 = tokenReissueService.reissue(refreshToken1).refreshToken

        val refreshToken3 = tokenReissueService.reissue(refreshToken2).refreshToken

        assertThat(jtiOf(refreshToken3)).isNotEqualTo(jtiOf(refreshToken2))
        assertThat(familyIdOf(refreshToken3)).isEqualTo(familyIdOf(refreshToken1))
    }

    @Test
    @DisplayName("유예 시간 - 교체 직후 이전 refreshToken으로 다시 요청하면 성공하고, 이미 교체된 같은 refreshToken을 다시 받음")
    fun gracePeriod_returnsSameRefreshToken() {
        saveUser("test@test.com", "nickname")
        val oldRefreshToken = loginAndGetRefreshToken("test@test.com")
        val first = tokenReissueService.reissue(oldRefreshToken)

        val second = tokenReissueService.reissue(oldRefreshToken)

        assertThat(jtiOf(second.refreshToken)).isEqualTo(jtiOf(first.refreshToken))
        assertThat(second.accessToken).isNotBlank()
        // 유예 요청은 교체를 다시 하지 않음
        val session = checkNotNull(refreshTokenRepository.findByFamilyId(familyIdOf(oldRefreshToken)))
        assertThat(session.currentJti).isEqualTo(jtiOf(first.refreshToken))
        assertThat(session.prevJti).isEqualTo(jtiOf(oldRefreshToken))
    }

    @Test
    @DisplayName("재사용 감지 - 유예 시간이 지난 뒤 이전 refreshToken을 쓰면 401-3, 세션이 삭제되어 최신 refreshToken도 무효")
    fun reuseDetected_afterGracePeriod_revokesSession() {
        saveUser("test@test.com", "nickname")
        val oldRefreshToken = loginAndGetRefreshToken("test@test.com")
        val newRefreshToken = tokenReissueService.reissue(oldRefreshToken).refreshToken
        val familyId = familyIdOf(oldRefreshToken)
        moveRotatedAtBack(familyId, Duration.ofSeconds(11))

        assertThatThrownBy { tokenReissueService.reissue(oldRefreshToken) }
            .satisfies({ e -> assertResultCode(e, "401-3") })

        // 예외를 던져도 세션 삭제는 롤백되지 않아야 함 (noRollbackFor)
        assertThat(refreshTokenRepository.findByFamilyId(familyId)).isNull()
        assertThatThrownBy { tokenReissueService.reissue(newRefreshToken) }
            .satisfies({ e -> assertResultCode(e, "401-2") })
    }

    @Test
    @DisplayName("재사용 감지 - 두 세대 이전 refreshToken은 유예 시간 안이어도 재사용으로 판단 (유예는 직전 1세대만)")
    fun reuseDetected_twoGenerationsOld() {
        saveUser("test@test.com", "nickname")
        val refreshToken1 = loginAndGetRefreshToken("test@test.com")
        val refreshToken2 = tokenReissueService.reissue(refreshToken1).refreshToken
        tokenReissueService.reissue(refreshToken2)

        assertThatThrownBy { tokenReissueService.reissue(refreshToken1) }
            .satisfies({ e -> assertResultCode(e, "401-3") })
        assertThat(refreshTokenRepository.findByFamilyId(familyIdOf(refreshToken1))).isNull()
    }

    @Test
    @DisplayName("재사용 감지 - 다른 기기(다른 세션)는 영향 없음")
    fun reuseDetected_otherSessionUnaffected() {
        saveUser("test@test.com", "nickname")
        val deviceA = loginAndGetRefreshToken("test@test.com")
        val deviceB = loginAndGetRefreshToken("test@test.com")
        tokenReissueService.reissue(deviceA)
        moveRotatedAtBack(familyIdOf(deviceA), Duration.ofSeconds(11))

        assertThatThrownBy { tokenReissueService.reissue(deviceA) }
            .satisfies({ e -> assertResultCode(e, "401-3") })

        assertThat(tokenReissueService.reissue(deviceB).accessToken).isNotBlank()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("트랜잭션 적용 - 테스트 트랜잭션 없이 reissue(String)(/refresh 경로)를 호출해도 교체 UPDATE가 실행됨")
    fun reissue_withoutTestTransaction_rotates() {
        // 클래스의 @Transactional이 테스트 트랜잭션을 미리 열어두면, 서비스에 트랜잭션이 없어도 UPDATE가 성공해서
        // self-invocation으로 @Transactional이 빠진 버그(TransactionRequiredException)를 잡지 못함 → 이 테스트만 트랜잭션 없이 실행
        // 데이터가 실제로 커밋되므로 끝나면 직접 정리
        val user = saveUser("no-tx@test.com", "noTxUser")
        var familyId: String? = null
        try {
            val oldRefreshToken = loginAndGetRefreshToken("no-tx@test.com")
            familyId = familyIdOf(oldRefreshToken)

            val result = tokenReissueService.reissue(oldRefreshToken)

            assertThat(jtiOf(result.refreshToken)).isNotEqualTo(jtiOf(oldRefreshToken))
            assertThat(checkNotNull(refreshTokenRepository.findByFamilyId(familyId)).currentJti)
                .isEqualTo(jtiOf(result.refreshToken))
        } finally {
            if (familyId != null) {
                refreshTokenRepository.findByFamilyId(familyId)?.let { refreshTokenRepository.delete(it) }
            }
            userRepository.delete(user)
        }
    }

    @Test
    @DisplayName("재발급 실패 - fid 클레임이 없는 refreshToken(Rotation 이전 방식)이면 401-2")
    fun reissue_fail_noFamilyId() {
        val user = saveUser("test@test.com", "nickname")
        val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret))
        val legacyRefreshToken = Jwts.builder()
            .subject(user.id.toString())
            .id(UUID.randomUUID().toString())
            .claim("token_type", "Refresh")
            .issuedAt(Date())
            .expiration(jwtProvider.newRefreshTokenExpiration())
            .signWith(key)
            .compact()

        assertThatThrownBy { tokenReissueService.reissue(legacyRefreshToken) }
            .satisfies({ e -> assertResultCode(e, "401-2") })
    }
}
