package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RelatedGameCalculationRepository : JpaRepository<RelatedGameCalculation, Long> {
    fun existsByGameIdAndLastSuccessAtIsNotNull(gameId: Long): Boolean

    // 최초 계산 미완료 대상을 ID 순서로 나누어 조회한다.
    @Query(
        """
            SELECT calculation.gameId FROM RelatedGameCalculation calculation
            WHERE calculation.lastSuccessAt IS NULL AND calculation.gameId > :afterGameId
            ORDER BY calculation.gameId

            """
    )
    fun findUnreadyGameIdsAfter(
        @Param("afterGameId") afterGameId: Long,
        pageable: Pageable,
    ): List<Long>
}
