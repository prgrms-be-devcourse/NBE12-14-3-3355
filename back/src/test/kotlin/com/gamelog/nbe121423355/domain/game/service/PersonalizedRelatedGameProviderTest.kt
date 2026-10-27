package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class PersonalizedRelatedGameProviderTest {
    @Mock
    private lateinit var repository: PersonalizedRelatedGameQueryRepository
    private lateinit var provider: PersonalizedRelatedGameProvider

    @BeforeEach
    fun setUp() {
        provider = PersonalizedRelatedGameProvider(repository)
        ReflectionTestUtils.setField(provider, "precomputedEnabled", true)
    }

    @Test
    fun emptySourcesDoNotQuery() {
        // given: 추천 출발 게임과 제외 대상이 모두 없는 요청을 준비한다.
        val sourceIds = emptyList<Long>()
        val excludedIds = emptyList<Long>()

        // when: 추천 출발 게임별 후보를 요청한다.
        val result = provider.findTopFiveBySourceGameIds(sourceIds, excludedIds)

        // then: 빈 결과를 반환하고 저장·실시간 조회를 모두 생략한다.
        assertThat(result).isEmpty()
        verifyNoInteractions(repository)
    }

    @Test
    fun disabledUsesOnlyExistingQuery() {
        // given: 저장 추천을 끄고 기존 쿼리의 빈 결과를 준비한다.
        ReflectionTestUtils.setField(provider, "precomputedEnabled", false)
        `when`(repository.findTopFiveBySourceGameIds(listOf(1L), listOf(9L))).thenReturn(emptyList())

        // when: 제외 대상을 지정하여 추천 후보를 요청한다.
        provider.findTopFiveBySourceGameIds(listOf(1L), listOf(9L))

        // then: 기존 실시간 쿼리만 같은 인자로 호출한다.
        verify(repository).findTopFiveBySourceGameIds(listOf(1L), listOf(9L))
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun sufficientStoredCandidatesAvoidRealtimeQuery() {
        // given: 기준 게임 하나에 저장 후보 다섯 개가 있는 상태를 준비한다.
        val stored = five(1L)
        `when`(repository.findStoredTopFiveBySourceGameIds(listOf(1L), emptyList())).thenReturn(stored)

        // when: 같은 기준 게임을 중복하여 후보를 요청한다.
        val result = provider.findTopFiveBySourceGameIds(listOf(1L, 1L), emptyList())

        // then: 기준 ID를 중복 제거하고 저장 후보만 반환하며 실시간 조회를 생략한다.
        assertThat(result).isEqualTo(stored)
        verify(repository).findStoredTopFiveBySourceGameIds(listOf(1L), emptyList())
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun replacesOnlyInsufficientSourcesInOneQueryWithoutDoubleCounting() {
        // given: 후보가 충분한 기준 게임과 부족하거나 후보가 없는 기준 게임을 준비한다.
        val complete = five(1L)
        val stale = row(2L, 99L, 1)
        `when`(repository.findStoredTopFiveBySourceGameIds(listOf(1L, 2L, 3L), listOf(8L)))
            .thenReturn(complete + stale)
        val replacement = listOf(row(2L, 20L, 1))
        `when`(repository.findTopFiveBySourceGameIds(listOf(2L, 3L), listOf(8L))).thenReturn(replacement)

        // when: 기준 게임을 역순으로 전달하여 후보를 요청한다.
        val result = provider.findTopFiveBySourceGameIds(listOf(3L, 2L, 1L), listOf(8L))

        // then: 부족한 기준만 일괄 재조회하고 기존 불충분 후보를 중복 합산하지 않는다.
        assertThat(result).containsExactlyElementsOf(complete + replacement)
        assertThat(result).doesNotContain(stale)
        verify(repository).findStoredTopFiveBySourceGameIds(listOf(1L, 2L, 3L), listOf(8L))
        verify(repository).findTopFiveBySourceGameIds(listOf(2L, 3L), listOf(8L))
        verifyNoMoreInteractions(repository)
    }

    private fun five(source: Long): List<PersonalizedRelatedGameProjection> =
        (1..5).map { rank -> row(source, source * 10 + rank, rank) }

    private fun row(source: Long, candidate: Long, rank: Int) = PersonalizedRelatedGameProjection(
        sourceGameId = source, gameId = candidate, title = "후보", coverImageUrl = null,
        igdbRating = BigDecimal("80"), relatedScore = BigDecimal("999"), relatedRank = rank,
    )
}
