package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame

data class UserFavoriteGameResponse(
    val gameId: Long,
    val title: String,
    val coverImageUrl: String?,
    val displayOrder: Int,
) {
    constructor(
        favoriteGame: UserFavoriteGame
    ) : this(
        gameId = checkNotNull(favoriteGame.game.id) { "favoriteGame.game.id must be present in a persisted response" },
        title = checkNotNull(favoriteGame.game.title) { "favoriteGame.game.title must be present in a persisted response" },
        coverImageUrl = favoriteGame.game.coverImageUrl,
        displayOrder = requireNotNull(favoriteGame.displayOrder),
    )
}
