package com.gamelog.nbe121423355.domain.usergame.support

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

internal fun apiGame(igdbId: Long, title: String): Game = Game.createFromIgdb(
    IgdbGameResponse(igdbId, title, null, null, null, null,
        mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()),
)

internal fun recordRequest(
    playStatus: PlayStatus? = PlayStatus.PLAYED,
    playing: Boolean = false,
    liked: Boolean = false,
    platformId: Long? = null,
    hours: BigDecimal? = null,
    finishHours: BigDecimal? = null,
    masterHours: BigDecimal? = null,
    startedAt: LocalDate? = null,
    completedAt: LocalDate? = null,
    lastPlayedAt: LocalDateTime? = null,
) = UserGameReqBody(
    playStatus = playStatus,
    isPlaying = playing,
    isBacklog = false,
    isWishlist = false,
    isLiked = liked,
    platformId = platformId,
    playTimeHours = hours,
    finishTimeHours = finishHours,
    masterTimeHours = masterHours,
    startedAt = startedAt,
    completedAt = completedAt,
    lastPlayedAt = lastPlayedAt,
)
