package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@JvmRecord
data class UserGameDto(
    val id: Long?,
    val userId: Long?,
    val gameId: Long?,
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
        userGame.id,
        userGame.user.id,
        userGame.game.id,
        userGame.platform?.id,
        userGame.playStatus,
        userGame.isPlaying,
        userGame.isBacklog,
        userGame.isWishlist,
        userGame.isLiked,
        userGame.playTimeHours,
        userGame.finishTimeHours,
        userGame.masterTimeHours,
        userGame.startedAt,
        userGame.completedAt,
        userGame.lastPlayedAt,
        userGame.isInLibrary,
    )
}
