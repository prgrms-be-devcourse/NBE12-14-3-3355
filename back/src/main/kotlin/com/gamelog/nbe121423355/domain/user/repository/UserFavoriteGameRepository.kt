package com.gamelog.nbe121423355.domain.user.repository

import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame
import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGameId
import org.springframework.data.jpa.repository.JpaRepository

interface UserFavoriteGameRepository : JpaRepository<UserFavoriteGame, UserFavoriteGameId> {
    fun findAllByUserIdOrderByDisplayOrderAsc(userId: Long?): MutableList<UserFavoriteGame>

    fun deleteAllByUserId(userId: Long?)

    fun deleteByUser_IdAndGame_Id(userId: Long?, gameId: Long?)
}
