package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class UserGameDto(
    val id: Long,
    val userId: Long,
    val gameId: Long,
    val platformId: Long?,
    val playStatus: PlayStatus?,
    val playing: Boolean,
    val backlog: Boolean,
    val wishlist: Boolean,
    val liked: Boolean,
    val playTimeHours: BigDecimal?,
    val finishTimeHours: BigDecimal?,
    val masterTimeHours: BigDecimal?,
    val startedAt: LocalDate?,
    val completedAt: LocalDate?,
    val lastPlayedAt: LocalDateTime?,
    val inLibrary: Boolean,
) {
    constructor(
        userGame: UserGame
    ) : this(
        id = checkNotNull(userGame.id) { "userGame.id must be present in a persisted response" },
        userId = checkNotNull(userGame.user.id) { "userGame.user.id must be present in a persisted response" },
        gameId = checkNotNull(userGame.game.id) { "userGame.game.id must be present in a persisted response" },
        platformId = userGame.platform?.id,
        playStatus = userGame.playStatus,
        playing = userGame.isPlaying,
        backlog = userGame.isBacklog,
        wishlist = userGame.isWishlist,
        liked = userGame.isLiked,
        playTimeHours = userGame.playTimeHours,
        finishTimeHours = userGame.finishTimeHours,
        masterTimeHours = userGame.masterTimeHours,
        startedAt = userGame.startedAt,
        completedAt = userGame.completedAt,
        lastPlayedAt = userGame.lastPlayedAt,
        inLibrary = userGame.isInLibrary,
    )
}
