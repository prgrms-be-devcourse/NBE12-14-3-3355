package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

class RelatedGamePrecomputeSnapshotTest {
    @Test
    fun reusesOneGenreRankingAndRetainsTwentyAfterSelfExclusion() {
        // given: 같은 장르 조합을 순서와 중복이 다르게 표현한 게임과 평점을 준비한다.
        val genres = (1L..25L).associateWith { id -> if (id % 2 == 0L) listOf(2L, 1L, 1L) else listOf(1L, 2L) }
        val ratings = (1L..25L).associateWith { BigDecimal("80.00") }
        val snapshot = snapshot(genres, ratings, emptySet())

        // when: 같은 장르 조합의 서로 다른 기준 게임으로 후보를 계산한다.
        val first = ids(snapshot, 1L)
        val second = ids(snapshot, 2L)
        val last = ids(snapshot, 25L)

        // then: 자기 자신을 제외한 20개를 유지하며 장르 조합 계산을 한 번만 수행한다.
        assertThat(first).containsExactlyElementsOf((2L..21L).toList())
        assertThat(second).containsExactlyElementsOf((1L..21L).filter { it != 2L })
        assertThat(last).containsExactlyElementsOf((1L..20L).toList())
        assertThat(snapshot.calculatedGroupCount()).isEqualTo(1)
    }

    @Test
    fun separatesPartiallyMatchingGenresAndPreservesFractionalScore() {
        // given: 일부 장르만 공유하는 기준 게임과 소수 평점의 후보를 준비한다.
        val snapshot = snapshot(
            mapOf(1L to listOf(1L, 2L), 2L to listOf(1L), 3L to listOf(1L, 2L), 4L to listOf(3L)),
            mapOf(3L to BigDecimal("80.25"), 4L to BigDecimal("99.00")),
            emptySet(),
        )

        // when: 서로 다른 장르 조합의 기본 추천을 계산한다.
        val first = requireNotNull(snapshot.baselineFor(1L))
        val second = requireNotNull(snapshot.baselineFor(2L))

        // then: 공유 장르 수와 소수 점수를 보존하고 조합별로 계산한다.
        assertThat(first.first().relatedScore).isEqualByComparingTo("70.125")
        assertThat(second.first().relatedScore).isEqualByComparingTo("55.125")
        assertThat(snapshot.calculatedGroupCount()).isEqualTo(2)
        assertThat(ids(snapshot, 1L)).containsExactly(3L)
    }

    @Test
    fun supportsMoreThanSixtyFourDistinctGenresWithoutBitCollisions() {
        // given: 비트마스크 한 단어를 넘는 70개 장르와 경계 양쪽의 후보를 준비한다.
        val genres = (1L..70L).associateWith { listOf(it) } + (100L to listOf(1L, 65L, 70L))
        val snapshot = snapshot(
            genres,
            mapOf(1L to BigDecimal("80"), 2L to BigDecimal("99"), 65L to BigDecimal("80"), 70L to BigDecimal("80")),
            emptySet(),
        )

        // when: 1번과 65번 및 70번 장르를 공유하는 후보를 계산한다.
        val result = ids(snapshot, 100L)

        // then: 서로 다른 비트 단어의 장르를 혼동하지 않는다.
        assertThat(result).containsExactly(1L, 65L, 70L)
    }

    @Test
    fun distinguishesEmptyResultFromSourcesThatNeedExistingQuery() {
        // given: 사용자 행동이 있는 게임과 장르가 없는 게임을 준비한다.
        val snapshot = snapshot(mapOf(1L to listOf(1L), 2L to emptyList()), emptyMap(), setOf(1L))

        // when: 행동 게임과 미포함 게임 및 장르 없는 게임의 기본 추천을 요청한다.
        val interaction = snapshot.baselineFor(1L)
        val missing = snapshot.baselineFor(999L)
        val empty = snapshot.baselineFor(2L)

        // then: SQL 계산이 필요한 null과 정상 빈 결과를 구분한다.
        assertThat(interaction).isNull()
        assertThat(missing).isNull()
        assertThat(empty).isNotNull().isEmpty()
    }

    @Test
    fun inputMutationDoesNotChangeSnapshotOrCachedRanking() {
        // given: 외부에서 수정 가능한 장르 목록과 후보 평점 및 행동 집합으로 스냅샷을 만든다.
        val sourceGenres = mutableListOf(1L)
        val genres = mutableMapOf(1L to sourceGenres, 2L to listOf(1L))
        val ratings = mutableMapOf(2L to BigDecimal("80.25"))
        val interactions = mutableSetOf<Long>()
        val snapshot = snapshot(genres, ratings, interactions)

        // when: 첫 계산 전 원본 입력을 변경하고 캐시 생성 후 장르 목록을 다시 변경한다.
        sourceGenres.clear()
        genres.clear()
        ratings.clear()
        interactions.add(1L)
        val first = requireNotNull(snapshot.baselineFor(1L))
        sourceGenres.add(999L)
        val repeated = snapshot.baselineFor(1L)

        // then: 최초 스냅샷의 점수와 후보 및 캐시 키를 유지한다.
        assertThat(first.map { it.gameId }).containsExactly(2L)
        assertThat(first.first().relatedScore).isEqualByComparingTo("55.125")
        assertThat(repeated).containsExactlyElementsOf(first)
        assertThat(snapshot.sourceCount()).isEqualTo(2)
        assertThat(snapshot.candidateCount()).isEqualTo(1)
        assertThat(snapshot.interactionSourceCount()).isZero()
        assertThat(snapshot.calculatedGroupCount()).isEqualTo(1)
    }

    @Test
    fun returnedBaselineCannotBeModified() {
        // given: 공유 장르를 가진 기준 게임과 후보의 스냅샷을 준비한다.
        val snapshot = snapshot(mapOf(1L to listOf(1L), 2L to listOf(1L)), mapOf(2L to BigDecimal("80")), emptySet())

        // when: 기준 게임의 기본 추천 목록을 조회하고 Java 컬렉션 API로 수정을 시도한다.
        val result = requireNotNull(snapshot.baselineFor(1L))
        val failure = catchThrowable { (result as MutableList).clear() }

        // then: 반환 목록을 수정할 수 없고 이후 조회에서도 같은 후보를 반환한다.
        assertThat(failure).isInstanceOf(UnsupportedOperationException::class.java)
        assertThat(ids(snapshot, 1L)).containsExactly(2L)
    }

    private fun snapshot(genres: Map<Long, List<Long>>, ratings: Map<Long, BigDecimal>, interactions: Set<Long>) =
        RelatedGamePrecomputeSnapshot(RelatedGamePrecomputeMode.INITIAL_LOAD, 20, genres, ratings, interactions)

    private fun ids(snapshot: RelatedGamePrecomputeSnapshot, sourceId: Long): List<Long> =
        requireNotNull(snapshot.baselineFor(sourceId)).map { it.gameId }
}
