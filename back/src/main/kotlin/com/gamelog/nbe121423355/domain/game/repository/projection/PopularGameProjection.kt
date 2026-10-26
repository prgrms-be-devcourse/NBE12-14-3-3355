package com.gamelog.nbe121423355.domain.game.repository.projection

interface PopularGameProjection {
    // 게임을 내부 조인해 조회하므로 ID와 필수 제목은 항상 존재한다.
    val gameId: Long

    val title: String

    val coverImageUrl: String?

    val likeCount: Long
}
