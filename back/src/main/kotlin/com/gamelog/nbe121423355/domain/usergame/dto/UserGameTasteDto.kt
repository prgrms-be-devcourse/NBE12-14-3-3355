package com.gamelog.nbe121423355.domain.usergame.dto

data class UserGameTasteDto(
    val longPlay: TasteMetricDto,
    val rating: TasteMetricDto,
    val completion: TasteMetricDto,
)
