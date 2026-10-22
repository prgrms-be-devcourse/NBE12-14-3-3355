package com.gamelog.nbe121423355.domain.usergame.dto

import java.math.BigDecimal

@JvmRecord
data class TasteMetricDto(
    val ratio: BigDecimal?,
    val message: String,
    val description: String,
) {}
