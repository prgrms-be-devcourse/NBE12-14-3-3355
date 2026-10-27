package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedGameRecordProjection
import java.math.BigDecimal
import org.springframework.stereotype.Component

// 게임 기록에서 연관 추천의 출발점으로 사용할 게임을 선택
@Component
class PersonalizedRecordSourceSelector {
    // 별점이 없거나 3.5점 이상인 기록 게임의 ID를 중복 없이 반환
    fun selectSourceGameIds(
        records: List<PersonalizedGameRecordProjection>
    ): List<Long> {
        val sourceIds = records.filter { record ->
            val rating = record.rating
            rating == null || rating >= MIN_SOURCE_RATING
        }.map { it.gameId }.distinct()
        return java.util.List.copyOf(sourceIds)
    }

    companion object {
        private val MIN_SOURCE_RATING = BigDecimal("3.5")
    }
}
