package com.gamelog.nbe121423355.domain.review.entity

import java.io.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ReviewLikeIdEqualityTest {
    enum class KeyType {
        REVIEW,
        COMMENT;

        fun create(userId: Long?, targetId: Long?): Serializable = when (this) {
            REVIEW -> ReviewLikeId(userId, targetId)
            COMMENT -> ReviewCommentLikeId(userId, targetId)
        }
    }

    @ParameterizedTest
    @EnumSource(KeyType::class)
    @DisplayName("같은 ID 값으로 따로 만든 키는 서로 같고 해시값도 같다")
    fun equalValuesHaveEqualHashes(type: KeyType) {
        val first = type.create(1000L, 2000L)
        val second = type.create(1000L, 2000L)
        val third = type.create(1000L, 2000L)

        assertThat(first).isEqualTo(first).isEqualTo(second)
        assertThat(second).isEqualTo(first).isEqualTo(third)
        assertThat(first).isEqualTo(third)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
    }

    @ParameterizedTest
    @EnumSource(KeyType::class)
    @DisplayName("사용자나 대상 ID가 다르면 다른 키다")
    fun differentComponentsAreNotEqual(type: KeyType) {
        val key = type.create(1000L, 2000L)

        assertThat(key).isNotEqualTo(type.create(1001L, 2000L))
        assertThat(key).isNotEqualTo(type.create(1000L, 2001L))
        assertThat(key).isNotEqualTo(type.create(2000L, 1000L))
    }

    @ParameterizedTest
    @EnumSource(KeyType::class)
    @DisplayName("null과 다른 종류의 키는 같지 않다")
    fun rejectsNullAndOtherTypes(type: KeyType) {
        val key = type.create(1000L, 2000L)
        val otherType = if (type == KeyType.REVIEW) KeyType.COMMENT else KeyType.REVIEW

        assertThat(key.equals(null)).isFalse()
        assertThat(key).isNotEqualTo("1000:2000")
        assertThat(key).isNotEqualTo(otherType.create(1000L, 2000L))
    }

    @ParameterizedTest
    @EnumSource(KeyType::class)
    @DisplayName("같은 ID 조합은 HashSet에서 중복되지 않고 HashMap의 키로 조회된다")
    fun worksAsHashCollectionKey(type: KeyType) {
        val first = type.create(1000L, 2000L)
        val same = type.create(1000L, 2000L)

        assertThat(hashSetOf(first, same)).hasSize(1)
        assertThat(hashMapOf(first to "좋아요")[same]).isEqualTo("좋아요")
    }

    @ParameterizedTest
    @EnumSource(KeyType::class)
    @DisplayName("JPA가 채우기 전 null 조합도 안전하게 비교하고 해시를 계산한다")
    fun handlesUninitializedAndPartialKeys(type: KeyType) {
        for ((userId, targetId) in listOf(null to null, 1000L to null, null to 2000L)) {
            val first = type.create(userId, targetId)
            val same = type.create(userId, targetId)

            assertThat(first).isEqualTo(same)
            assertThat(first.hashCode()).isEqualTo(same.hashCode())
        }
        assertThat(type.create(null, 2000L)).isNotEqualTo(type.create(1000L, 2000L))
        assertThat(type.create(1000L, null)).isNotEqualTo(type.create(1000L, 2000L))
    }
}
