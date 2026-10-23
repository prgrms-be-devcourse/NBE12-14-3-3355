package com.gamelog.nbe121423355.domain.game.service.model

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("recommendation.admin.precompute")
data class RelatedGamePrecomputeAdminProperties(
    // 계산과 독립된 생존 신호 12회 누락을 기본 기준으로 삼고, 잠금 재확인도 필수로 수행한다.
    val heartbeatInterval: Duration = Duration.ofSeconds(5),
    val staleAfter: Duration = Duration.ofMinutes(1),
    val recoveryInterval: Duration = Duration.ofSeconds(30),
) {
    init {
        require(heartbeatInterval >= Duration.ofSeconds(1))
        require(staleAfter >= heartbeatInterval.multipliedBy(3))
        require(recoveryInterval >= Duration.ofSeconds(1))
    }
}
