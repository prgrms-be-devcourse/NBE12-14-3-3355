package com.gamelog.nbe121423355.domain.usergame.dto

import io.swagger.v3.oas.annotations.media.Schema

@JvmRecord
data class UserFavoriteGameRequest(
    @field:Schema(description = "표시 순서대로 전달하는 인생 게임 ID 목록", example = "[6, 9, 12]")
    val gameIds: MutableList<Long>?
) {}
