package com.gamelog.nbe121423355.domain.game.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RelatedGameCandidateIdTest {
    @Test
    fun `동일한 두 게임 ID는 같은 키이며 기존 해시 값과 집합 조회를 유지한다`() {
        // given: 같은 ID 쌍으로 만든 별도 객체와 집합을 준비한다.
        val key = RelatedGameCandidateId(1L, 2L)
        val equivalent = RelatedGameCandidateId(1L, 2L)
        val keys = hashSetOf(key)

        // when: 별도 생성한 키로 집합을 조회한다.
        val found = equivalent in keys

        // then: 동등성과 기존 해시 값 및 집합 조회를 유지한다.
        assertThat(key).isEqualTo(equivalent)
        assertThat(equivalent).isEqualTo(key)
        assertThat(key.hashCode()).isEqualTo(3542)
        assertThat(equivalent.hashCode()).isEqualTo(key.hashCode())
        assertThat(found).isTrue()
    }

    @Test
    fun `게임 ID 순서나 한쪽 ID가 다르면 다른 추천 후보 키다`() {
        // given: 기준 게임과 후보 게임의 순서 또는 후보 ID가 다른 키를 준비한다.
        val key = RelatedGameCandidateId(1L, 2L)
        val reversed = RelatedGameCandidateId(2L, 1L)
        val otherCandidate = RelatedGameCandidateId(1L, 3L)

        // when: 세 키를 집합에 저장한다.
        val keys = setOf(key, reversed, otherCandidate)

        // then: 방향과 각 ID를 구분하여 세 키를 별개로 취급한다.
        assertThat(keys).hasSize(3)
        assertThat(key).isNotEqualTo(reversed).isNotEqualTo(otherCandidate)
        assertThat(key.equals(null)).isFalse()
        assertThat(key.equals("1,2")).isFalse()
    }

    @Test
    fun `JPA가 ID를 채우기 전의 무인자 키도 기존 null 동등성과 해시를 유지한다`() {
        // given: JPA가 사용하는 무인자 생성자로 ID가 비어 있는 키를 준비한다.
        val constructor = RelatedGameCandidateId::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val first = constructor.newInstance()
        val second = constructor.newInstance()

        // when: 아직 ID가 채워지지 않은 두 키를 비교한다.
        val equal = first == second

        // then: 초기 ID의 null과 기존 해시 값을 보존한다.
        assertThat(first.sourceGameId).isNull()
        assertThat(first.candidateGameId).isNull()
        assertThat(equal).isTrue()
        assertThat(first.hashCode()).isEqualTo(6061)
        assertThat(second.hashCode()).isEqualTo(first.hashCode())
    }
}
