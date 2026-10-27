package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunFailure
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface RelatedGamePrecomputeRunFailureRepository :
    JpaRepository<RelatedGamePrecomputeRunFailure, Long> {
    fun findByRun_IdOrderByGameIdAsc(
        runId: String,
        pageable: Pageable,
    ): Page<RelatedGamePrecomputeRunFailure>
}
