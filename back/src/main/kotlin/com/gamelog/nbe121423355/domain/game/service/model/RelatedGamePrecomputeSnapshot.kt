package com.gamelog.nbe121423355.domain.game.service.model

import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import com.gamelog.nbe121423355.global.interop.javaNonNull
import java.math.BigDecimal
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import java.util.Optional
import java.util.PriorityQueue

/** 한 실행에서 공유하는 계산 데이터와 장르 조합별 기본 순위 캐시. */
class RelatedGamePrecomputeSnapshot(
    mode: RelatedGamePrecomputeMode?,
    private val limit: Int,
    sourceGenres: MutableMap<Long, MutableList<Long>>,
    eligibleRatings: MutableMap<Long, BigDecimal>,
    interactionSources: MutableSet<Long>?,
) {
    private val mode: RelatedGamePrecomputeMode?
    private val sourceGenres: MutableMap<Long, MutableList<Long>>
    private val interactionSources: MutableSet<Long>
    private val candidates: MutableList<Candidate>
    private val genreMasks: MutableMap<MutableList<Long>, LongArray> = HashMap()
    private val rankings:
        MutableMap<MutableList<Long>, MutableList<RelatedGameCandidateProjection>> =
        HashMap()

    init {
        this.mode = javaNonNull(mode, "mode")
        require(limit >= 1) { "저장 후보 수는 1 이상이어야 합니다." }

        this.interactionSources = java.util.Set.copyOf(interactionSources)

        // 같은 장르 집합은 순서와 중복에 관계없이 하나의 불변 키로 정규화한다.
        val canonicalGenres: MutableMap<MutableList<Long>, MutableList<Long>> = HashMap()
        val normalized: MutableMap<Long, MutableList<Long>> = HashMap()
        sourceGenres.forEach({ id, genres ->
            val key = genres.stream().distinct().sorted().toList()
            normalized.put(id, canonicalGenres.computeIfAbsent(key, { ignored -> key }))
        })
        this.sourceGenres = Collections.unmodifiableMap(normalized)

        val genreIndexes: MutableMap<Long, Int> = HashMap()
        canonicalGenres.keys.forEach({ genres ->
            genres.forEach({ genre ->
                genreIndexes.computeIfAbsent(genre, { ignored -> genreIndexes.size })
            })
        })
        val wordCount = (genreIndexes.size + Long.SIZE_BITS - 1) / Long.SIZE_BITS
        canonicalGenres.keys.forEach({ genres ->
            val words = LongArray(wordCount)
            for (genre in genres) {
                val index = genreIndexes.getValue(genre)
                words[index / Long.SIZE_BITS] =
                    words[index / Long.SIZE_BITS] or (1L shl (index % Long.SIZE_BITS))
            }
            genreMasks.put(genres, words)
        })

        val prepared: MutableList<Candidate> = ArrayList()
        eligibleRatings.forEach({ id, rating ->
            val genres = this.sourceGenres.get(id)
            if (genres != null && !genres.isEmpty()) {
                // DB의 IGDB 소수 둘째 자리 × 0.5를 천분의 일 단위 정수로 정확히 표현한다.
                val igdbScore =
                    rating.multiply(BigDecimal("0.5")).movePointRight(3).longValueExact()
                prepared.add(Candidate(id, genreMasks.getValue(genres), igdbScore))
            }
        })
        this.candidates = java.util.List.copyOf(prepared)
    }

    fun mode(): RelatedGamePrecomputeMode? {
        return mode
    }

    /** 비어 있는 Optional은 기존 SQL 계산이 필요한 기준 게임을 뜻한다. */
    fun baselineFor(sourceId: Long?): Optional<MutableList<RelatedGameCandidateProjection>> {
        val genres = sourceGenres.get(sourceId)
        if (genres == null) {
            return Optional.empty()
        }
        if (genres.isEmpty()) {
            return Optional.of(java.util.List.of())
        }
        if (interactionSources.contains(sourceId)) {
            return Optional.empty()
        }
        val groupRanking: MutableList<RelatedGameCandidateProjection> =
            rankings.computeIfAbsent(
                genres,
                { genres: MutableList<Long> -> this.rankGroup(genres) },
            )
        return Optional.of(
            groupRanking
                .stream()
                .filter({ candidate -> candidate.gameId != sourceId })
                .limit(limit.toLong())
                .toList()
        )
    }

    fun sourceCount(): Int {
        return sourceGenres.size
    }

    fun candidateCount(): Int {
        return candidates.size
    }

    fun interactionSourceCount(): Int {
        return interactionSources.size
    }

    fun calculatedGroupCount(): Int {
        return rankings.size
    }

    private fun rankGroup(genres: MutableList<Long>): MutableList<RelatedGameCandidateProjection> {
        val sourceMask = genreMasks.getValue(genres)
        // 자신의 후보 제외로 한 자리가 빠질 수 있으므로 그룹에서는 N+1개를 보관한다.
        val groupLimit = limit + 1
        val worstFirst =
            Comparator.comparingLong(ScoredCandidate::score)
                .thenComparing(ScoredCandidate::id, Comparator.reverseOrder())
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
        return top.stream()
            .sorted(worstFirst.reversed())
            .map({ candidate ->
                RelatedGameCandidateProjection(
                    candidate.id,
                    BigDecimal.valueOf(candidate.score, 3),
                )
            })
            .toList()
    }

    @JvmRecord
    private data class Candidate(val id: Long, val genres: LongArray, val igdbScore: Long) {}

    @JvmRecord private data class ScoredCandidate(val id: Long, val score: Long) {}

    companion object {
        private const val SHARED_GENRE_SCORE: Long = 15000L
    }
}
