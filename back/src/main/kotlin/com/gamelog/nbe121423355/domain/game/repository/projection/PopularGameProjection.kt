package com.gamelog.nbe121423355.domain.game.repository.projection

interface PopularGameProjection {
    val gameId: Long?

    val title: String?

    val coverImageUrl: String?

    val likeCount: Long
}
