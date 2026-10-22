package com.gamelog.nbe121423355.domain.game.dto

import java.math.BigDecimal

// 게임의 평균 플레이타임과 플레이타임 기록 사용자 수를 반환
@JvmRecord
data class GamePlayTimeStatisticsResponse(
    val averagePlayTimeHours: BigDecimal?,
    val playTimeUserCount: Long,
) {}
