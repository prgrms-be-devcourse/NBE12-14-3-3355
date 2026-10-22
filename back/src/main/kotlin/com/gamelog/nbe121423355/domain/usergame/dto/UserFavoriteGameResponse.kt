package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame

@JvmRecord
data class UserFavoriteGameResponse(
    val gameId: Long,
    val title: String,
    val coverImageUrl: String?,
    val displayOrder: Int,
) {
    constructor(
        favoriteGame: UserFavoriteGame
    ) : this(
        checkNotNull(favoriteGame.game.id) { "favoriteGame.game.id must be present in a persisted response" },
        checkNotNull(favoriteGame.game.title) { "favoriteGame.game.title must be present in a persisted response" },
        favoriteGame.game.coverImageUrl,
        requireNotNull(favoriteGame.displayOrder),
    )
}
