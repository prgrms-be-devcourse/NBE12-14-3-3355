package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.Platform
import org.springframework.data.jpa.repository.JpaRepository

interface PlatformRepository : JpaRepository<Platform, Long> {
    fun findByIgdbPlatformsId(igdbPlatformsId: Long?): Platform?
}
