package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.user.repository.RefreshTokenRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

// 만료된 refreshToken 정기 삭제
// 만료된 토큰은 재발급에 쓰일 수 없지만(JWT exp 검증) DB 행이 계속 쌓이므로 하루 한 번 정리
@Component
class RefreshTokenCleanupScheduler(private val refreshTokenRepository: RefreshTokenRepository) {
    // 매일 새벽 4시(한국 시간), 트래픽이 적은 시간대
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    fun deleteExpiredRefreshTokens() {
        // expiryDate는 로그인 시 ZoneId.systemDefault() 기준으로 저장되므로 비교도 같은 기준(LocalDateTime.now())
        val deleted = refreshTokenRepository.deleteAllExpiredBefore(LocalDateTime.now())
        log.info("만료된 RefreshToken {}건 삭제", deleted)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler::class.java)
    }
}
