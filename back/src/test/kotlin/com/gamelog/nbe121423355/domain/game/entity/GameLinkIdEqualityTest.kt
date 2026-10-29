package com.gamelog.nbe121423355.domain.game.entity

import java.io.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class GameLinkIdEqualityTest {
    enum class KeyType(val keyClass: Class<out Serializable>) {
        GENRE(GameGenreId::class.java),
        PLATFORM(GamePlatformId::class.java),
        SERIES(GameSeriesGameId::class.java);

        fun create(first: Long?, second: Long?): Serializable = when (this) {
            GENRE -> GameGenreId(first, second)
            PLATFORM -> GamePlatformId(first, second)
            SERIES -> GameSeriesGameId(first, second)
        }
    }

    @ParameterizedTest
    @EnumSource(KeyType::class)
    fun `동일한 ID 쌍은 기존 해시 값과 집합 조회를 유지한다`(type: KeyType) {
        // given: 같은 값을 가진 별도 키와 기준 키를 담은 집합을 준비한다.
        val first = type.create(1L, 2L)
        val same = type.create(1L, 2L)
        val keys = hashSetOf(first)

        // when: 같은 값의 별도 키로 집합을 조회한다.
        val found = same in keys

        // then: 대칭적 동등성, 기존 해시 값과 해시 기반 조회를 유지한다.
        assertThat(first).isEqualTo(same)
        assertThat(same).isEqualTo(first)
        assertThat(first.hashCode()).isEqualTo(3542)
        assertThat(same.hashCode()).isEqualTo(first.hashCode())
        assertThat(found).isTrue()
    }

    @ParameterizedTest
    @EnumSource(KeyType::class)
    fun `ID 순서와 각 값 및 키 종류를 구분한다`(type: KeyType) {
        // given: 순서 또는 한쪽 값이 다른 키와 다른 종류의 키를 준비한다.
        val key = type.create(1L, 2L)
        val reversed = type.create(2L, 1L)
        val otherValue = type.create(1L, 3L)
        val otherType = KeyType.entries.first { it != type }.create(1L, 2L)

        // when: 서로 다른 키를 집합에 저장한다.
        val keys = setOf(key, reversed, otherValue, otherType)

        // then: 다른 조합과 종류를 별개로 취급하며 null과 문자열도 같지 않다.
        assertThat(keys).hasSize(4)
        assertThat(key.equals(null)).isFalse()
        assertThat(key.equals("1,2")).isFalse()
    }

    @ParameterizedTest
    @EnumSource(KeyType::class)
    fun `JPA 무인자 생성과 일부 ID가 빈 상태에서도 기존 비교 규칙을 유지한다`(type: KeyType) {
        // given: JPA 무인자 생성자로 만든 키와 일부 ID가 비어 있는 키를 준비한다.
        val constructor = type.keyClass.getDeclaredConstructor().apply { isAccessible = true }
        val empty = constructor.newInstance()
        val sameEmpty = type.create(null, null)
        val partial = type.create(1L, null)

        // when: 초기 상태와 부분 초기화 상태를 비교한다.
        val emptyEqual = empty == sameEmpty
        val partialEqual = partial == type.create(1L, null)

        // then: null 조합의 동등성과 기존 해시를 보존하고 채워진 키와 구분한다.
        assertThat(emptyEqual).isTrue()
        assertThat(empty.hashCode()).isEqualTo(6061)
        assertThat(partialEqual).isTrue()
        assertThat(partial.hashCode()).isEqualTo(3583)
        assertThat(partial).isNotEqualTo(type.create(1L, 2L))
    }
}
