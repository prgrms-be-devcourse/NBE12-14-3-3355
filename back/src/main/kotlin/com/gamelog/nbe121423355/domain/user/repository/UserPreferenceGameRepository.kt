package com.gamelog.nbe121423355.domain.user.repository

import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGame
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGameId
import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceGameRepository : JpaRepository<UserPreferenceGame, UserPreferenceGameId> {
    fun findByUser_Id(id: Long?): MutableList<UserPreferenceGame>

    fun deleteByUser_Id(id: Long?)
}
