package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame

@JvmRecord
data class UserFavoriteGameResponse(
    val gameId: Long?,
    val title: String?,
    val coverImageUrl: String?,
    val displayOrder: Int,
) {
    constructor(
        favoriteGame: UserFavoriteGame
    ) : this(
        favoriteGame.game.id,
        favoriteGame.game.title,
        favoriteGame.game.coverImageUrl,
        requireNotNull(favoriteGame.displayOrder),
    )
}
