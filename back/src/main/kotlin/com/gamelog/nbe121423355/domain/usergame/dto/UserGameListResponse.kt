package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame

data class UserGameListResponse(
    val gameId: Long,
    val title: String,
    val coverImageUrl: String?,
    val playStatus: PlayStatus?,
    val playing: Boolean,
    val backlog: Boolean,
    val wishlist: Boolean,
    val liked: Boolean,
) {
    constructor(
        userGame: UserGame
    ) : this(
        gameId = checkNotNull(userGame.game.id) { "userGame.game.id must be present in a persisted response" },
        title = checkNotNull(userGame.game.title) { "userGame.game.title must be present in a persisted response" },
        coverImageUrl = userGame.game.coverImageUrl,
        playStatus = userGame.playStatus,
        playing = userGame.isPlaying,
        backlog = userGame.isBacklog,
        wishlist = userGame.isWishlist,
        liked = userGame.isLiked,
    )
}
