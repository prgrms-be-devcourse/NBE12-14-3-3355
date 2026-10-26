package com.gamelog.nbe121423355.domain.game.repository.projection

import java.math.BigDecimal

// 추천에 사용할 게임 기록의 게임 ID, 장르 ID, 사용자 별점을 매핑
@JvmRecord
data class PersonalizedGameRecordProjection(
    val gameId: Long,
    val genreId: Long?,
    val rating: BigDecimal?,
)
