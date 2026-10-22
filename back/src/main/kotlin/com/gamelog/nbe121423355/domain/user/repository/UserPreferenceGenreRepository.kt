package com.gamelog.nbe121423355.domain.user.repository

import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGenre
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGenreId
import org.springframework.data.jpa.repository.JpaRepository

interface UserPreferenceGenreRepository :
    JpaRepository<UserPreferenceGenre, UserPreferenceGenreId> {
    fun findByUser_Id(id: Long?): MutableList<UserPreferenceGenre>

    fun deleteByUser_Id(id: Long?)
}
