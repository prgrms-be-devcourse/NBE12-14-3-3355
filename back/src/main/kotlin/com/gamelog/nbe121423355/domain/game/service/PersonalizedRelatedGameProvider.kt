package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PersonalizedRelatedGameProvider(private val repository: PersonalizedRelatedGameQueryRepository) {
    @Value("\${recommendation.personalized.precomputed-enabled:false}")
    private var precomputedEnabled = false

    fun findTopFiveBySourceGameIds(
        sourceGameIds: List<Long>,
        excludedGameIds: List<Long>,
    ): List<PersonalizedRelatedGameProjection> {
        if (sourceGameIds.isEmpty()) return emptyList()
        val sources = sourceGameIds.distinct().sorted()
        if (!precomputedEnabled) {
            return repository.findTopFiveBySourceGameIds(sources, excludedGameIds)
        }
        val stored =
            repository.findStoredTopFiveBySourceGameIds(sources, excludedGameIds)
        val counts = stored.groupingBy { it.sourceGameId }.eachCount()
        val insufficient = sources.filter { counts.getOrDefault(it, 0) < 5 }
        if (insufficient.isEmpty()) return stored

        val replacedSources = insufficient.toSet()
        // 부족한 기준 게임의 목록 전체를 교체하여 저장 후보와 중복 합산하지 않는다.
        val retained = stored.filter { it.sourceGameId !in replacedSources }
        val replacements = repository.findTopFiveBySourceGameIds(insufficient, excludedGameIds)
        val result = (retained + replacements).sortedWith(
            compareBy<PersonalizedRelatedGameProjection> { it.sourceGameId }.thenBy { it.relatedRank }
        )
        return java.util.List.copyOf(result)
    }
}
