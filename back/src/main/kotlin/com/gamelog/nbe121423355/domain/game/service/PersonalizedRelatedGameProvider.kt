package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import java.util.ArrayList
import java.util.Comparator
import java.util.stream.Collectors
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PersonalizedRelatedGameProvider
constructor(private val repository: PersonalizedRelatedGameQueryRepository) {
    @Value("\${recommendation.personalized.precomputed-enabled:false}")
    private var precomputedEnabled = false

    fun findTopFiveBySourceGameIds(
        sourceGameIds: MutableList<Long>,
        excludedGameIds: MutableList<Long>,
    ): MutableList<PersonalizedRelatedGameProjection> {
        if (sourceGameIds.isEmpty()) return java.util.List.of()
        val sources = sourceGameIds.stream().distinct().sorted().toList()
        if (!precomputedEnabled) {
            return repository.findTopFiveBySourceGameIds(sources, excludedGameIds)
        }
        val stored: MutableList<PersonalizedRelatedGameProjection> =
            repository.findStoredTopFiveBySourceGameIds(sources, excludedGameIds)
        val counts =
            stored
                .stream()
                .collect(
                    Collectors.groupingBy(
                        PersonalizedRelatedGameProjection::sourceGameId,
                        Collectors.counting(),
                    )
                )
        val insufficient =
            sources.stream().filter({ id -> counts.getOrDefault(id, 0L) < 5 }).toList()
        if (insufficient.isEmpty()) return stored
        val replacedSources = java.util.Set.copyOf(insufficient)
        val result: MutableList<PersonalizedRelatedGameProjection> =
            ArrayList(
                stored
                    .stream()
                    .filter({ row -> !replacedSources.contains(row.sourceGameId) })
                    .toList()
            )
        // 부족한 기준 게임의 목록 전체를 교체하여 저장 후보와 중복 합산하지 않는다.
        result.addAll(repository.findTopFiveBySourceGameIds(insufficient, excludedGameIds))
        result.sortWith(
            Comparator.comparing(PersonalizedRelatedGameProjection::sourceGameId)
                .thenComparingInt(PersonalizedRelatedGameProjection::relatedRank)
        )
        return java.util.List.copyOf(result)
    }
}
