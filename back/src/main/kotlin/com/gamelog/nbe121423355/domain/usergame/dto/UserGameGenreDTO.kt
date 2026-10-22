package com.gamelog.nbe121423355.domain.usergame.dto

@JvmRecord
data class UserGameGenreDTO(
    val genreId: Long?,
    val genreName: String?,
    val gameCount: Long,
) {}
