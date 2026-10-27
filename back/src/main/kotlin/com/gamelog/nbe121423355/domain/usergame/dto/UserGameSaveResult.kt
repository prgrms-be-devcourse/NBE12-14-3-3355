package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.usergame.entity.UserGame

data class UserGameSaveResult(
    val userGame: UserGame,
    val created: Boolean,
)
