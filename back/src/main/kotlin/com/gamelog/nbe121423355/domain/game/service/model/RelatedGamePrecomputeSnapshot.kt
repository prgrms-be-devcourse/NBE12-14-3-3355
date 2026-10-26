package com.gamelog.nbe121423355.domain.game.service.model

import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import java.math.BigDecimal
import java.util.PriorityQueue

/** 한 실행에서 공유하는 계산 데이터와 장르 조합별 기본 순위 캐시. */
class RelatedGamePrecomputeSnapshot(
    val mode: RelatedGamePrecomputeMode,
    private val limit: Int,
    sourceGenres: Map<Long, List<Long>>,
    eligibleRatings: Map<Long, BigDecimal>,
    interactionSources: Set<Long>,
) {
    private val sourceGenres: Map<Long, List<Long>>
    private val interactionSources = java.util.Set.copyOf(interactionSources)
    private val candidates: List<Candidate>
    private val genreMasks = mutableMapOf<List<Long>, LongArray>()
    private val rankings = mutableMapOf<List<Long>, List<RelatedGameCandidateProjection>>()

    init {
        require(limit >= 1) { "저장 후보 수는 1 이상이어야 합니다." }

        // 같은 장르 집합은 순서와 중복에 관계없이 하나의 불변 키로 정규화한다.
        val canonicalGenres = mutableMapOf<List<Long>, List<Long>>()
        val normalized = sourceGenres.mapValues { (_, genres) ->
            val key = java.util.List.copyOf(genres.distinct().sorted())
            canonicalGenres.getOrPut(key) { key }
        }
        this.sourceGenres = java.util.Map.copyOf(normalized)

        val genreIndexes = mutableMapOf<Long, Int>()
        for (genres in canonicalGenres.keys) {
            for (genre in genres) {
                genreIndexes.getOrPut(genre) { genreIndexes.size }
            }
        }
        val wordCount = (genreIndexes.size + Long.SIZE_BITS - 1) / Long.SIZE_BITS
        for (genres in canonicalGenres.keys) {
            val words = LongArray(wordCount)
            for (genre in genres) {
                val index = genreIndexes.getValue(genre)
                words[index / Long.SIZE_BITS] =
                    words[index / Long.SIZE_BITS] or (1L shl (index % Long.SIZE_BITS))
            }
            genreMasks[genres] = words
        }

        val prepared = eligibleRatings.mapNotNull { (id, rating) ->
            val genres = this.sourceGenres[id]
            if (genres.isNullOrEmpty()) {
                null
            } else {
                // DB의 IGDB 소수 둘째 자리 × 0.5를 천분의 일 단위 정수로 정확히 표현한다.
                val igdbScore = rating.multiply(IGDB_RATING_WEIGHT).movePointRight(3).longValueExact()
                Candidate(id = id, genres = genreMasks.getValue(genres), igdbScore = igdbScore)
            }
        }
        this.candidates = java.util.List.copyOf(prepared)
    }

    /** null은 기존 SQL 계산이 필요함을, 빈 목록은 후보가 없는 정상 계산 결과를 뜻한다. */
    fun baselineFor(sourceId: Long): List<RelatedGameCandidateProjection>? {
        val genres = sourceGenres[sourceId] ?: return null
        if (genres.isEmpty()) return emptyList()
        if (sourceId in interactionSources) return null

        val groupRanking = rankings.getOrPut(genres) { rankGroup(genres) }
        val baseline = groupRanking.asSequence()
            .filter { it.gameId != sourceId }
            .take(limit)
            .toList()
        // 실행 내 캐시를 공유하되 호출자가 반환 결과를 변경하지 못하게 한다.
        return java.util.List.copyOf(baseline)
    }

    fun sourceCount(): Int = sourceGenres.size

    fun candidateCount(): Int = candidates.size

    fun interactionSourceCount(): Int = interactionSources.size

    fun calculatedGroupCount(): Int = rankings.size

    private fun rankGroup(genres: List<Long>): List<RelatedGameCandidateProjection> {
        val sourceMask = genreMasks.getValue(genres)
        // 자신의 후보 제외로 한 자리가 빠질 수 있으므로 그룹에서는 N+1개를 보관한다.
        val groupLimit = limit + 1
        val worstFirst = compareBy<ScoredCandidate> { it.score }.thenByDescending { it.id }
        val top = PriorityQueue(groupLimit, worstFirst)
        for (candidate in candidates) {
            var shared = 0
            for (word in sourceMask.indices) {
                shared += java.lang.Long.bitCount(sourceMask[word] and candidate.genres[word])
            }
            if (shared == 0) {
                continue
            }
            val score = candidate.igdbScore + shared * SHARED_GENRE_SCORE
            val worst = top.peek()
            if (
                top.size < groupLimit ||
                    score > worst.score ||
                    (score == worst.score && candidate.id < worst.id)
            ) {
                if (top.size == groupLimit) {
                    top.poll()
                }
                top.add(ScoredCandidate(candidate.id, score))
            }
        }
        val ranking = top.sortedWith(worstFirst.reversed())
            .map { candidate ->
                RelatedGameCandidateProjection(
                    gameId = candidate.id,
                    relatedScore = BigDecimal.valueOf(candidate.score, 3),
                )
            }
        return java.util.List.copyOf(ranking)
    }

    private data class Candidate(val id: Long, val genres: LongArray, val igdbScore: Long)

    private data class ScoredCandidate(val id: Long, val score: Long)

    companion object {
        private const val SHARED_GENRE_SCORE = 15000L
        private val IGDB_RATING_WEIGHT = BigDecimal("0.5")
    }
}
