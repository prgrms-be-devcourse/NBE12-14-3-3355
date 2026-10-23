package com.gamelog.nbe121423355.domain.usergame.dto

data class UserGameLibraryResponse(
    val totalElements: Long,
    val totalPages: Int,
    val userGames: List<UserGameListResponse>,
) {}
