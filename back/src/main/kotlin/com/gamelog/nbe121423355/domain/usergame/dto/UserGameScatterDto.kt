package com.gamelog.nbe121423355.domain.usergame.dto

import java.math.BigDecimal

data class UserGameScatterDto(
    val gameId: Long,
    val title: String,
    val coverImage: String?,
    val playTime: BigDecimal,
    val rating: BigDecimal,
)
