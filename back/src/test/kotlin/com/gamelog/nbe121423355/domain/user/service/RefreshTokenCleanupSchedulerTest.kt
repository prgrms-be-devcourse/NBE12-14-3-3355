package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.user.entity.RefreshToken
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.RefreshTokenRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class RefreshTokenCleanupSchedulerTest
@Autowired
constructor(
    private val refreshTokenCleanupScheduler: RefreshTokenCleanupScheduler,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
) {
    @Test
    @DisplayName("만료된 RefreshToken만 삭제되고, 아직 유효한 RefreshToken은 남음")
    fun deleteExpiredRefreshTokens_onlyExpired() {
        val user = userRepository.save(User("nickname", "test@test.com", "encoded-password"))
        refreshTokenRepository.save(RefreshToken.create(user, "expired-family", "expired-jti", LocalDateTime.now().minusDays(1)))
        refreshTokenRepository.save(RefreshToken.create(user, "valid-family", "valid-jti", LocalDateTime.now().plusDays(1)))

        refreshTokenCleanupScheduler.deleteExpiredRefreshTokens()

        assertThat(refreshTokenRepository.findByFamilyId("expired-family")).isNull()
        assertThat(refreshTokenRepository.findByFamilyId("valid-family")).isNotNull()
    }
}
