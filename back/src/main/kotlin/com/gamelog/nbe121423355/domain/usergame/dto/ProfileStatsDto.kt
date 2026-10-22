package com.gamelog.nbe121423355.domain.usergame.dto

import java.math.BigDecimal

@JvmRecord
data class ProfileStatsDto(
    val playedGameCount: Long,
    val averageRating: BigDecimal?,
    val totalPlayTime: BigDecimal?,
) {}
