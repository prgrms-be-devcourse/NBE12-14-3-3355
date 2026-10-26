package com.gamelog.nbe121423355.domain.usergame.dto

import java.math.BigDecimal

data class UserGameGenreDistributionResponse(
    val genreName: String,
    val ratio: BigDecimal,
)
