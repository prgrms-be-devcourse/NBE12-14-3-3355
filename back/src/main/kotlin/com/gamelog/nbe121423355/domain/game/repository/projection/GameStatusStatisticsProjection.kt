package com.gamelog.nbe121423355.domain.game.repository.projection

// GameStatisticsRepository 집계 쿼리의 alias를 동일한 이름의 getter에 매핑
interface GameStatusStatisticsProjection {
    val playedCount: Long

    val playingCount: Long

    val backlogCount: Long

    val wishlistCount: Long
}
