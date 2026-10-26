package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.service.NewGameRecommendationRegistration.GamesAdded
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NewGameRecommendationEventTest {
    @Test
    fun `이벤트 발행 후 원본 목록을 변경해도 계산할 게임 목록은 유지된다`() {
        // given: 변경 가능한 원본 목록에서 신규 게임 이벤트를 만든다.
        val ids = mutableListOf(10L, 20L)
        val event = GamesAdded(ids)

        // when: 이벤트 생성 후 발행자가 원본 목록을 변경한다.
        ids.clear()
        ids.add(30L)

        // then: 이벤트는 최초 목록과 값 동등성을 유지한다.
        assertThat(event.gameIds).containsExactly(10L, 20L)
        assertThat(event).isEqualTo(GamesAdded(listOf(10L, 20L)))
        assertThat(event.hashCode()).isEqualTo(GamesAdded(listOf(10L, 20L)).hashCode())
    }

    @Test
    fun `이벤트 수신자는 계산할 게임 목록을 수정할 수 없다`() {
        // given: 신규 게임 두 개를 담은 이벤트를 준비한다.
        val event = GamesAdded(listOf(10L, 20L))

        // when: Java 컬렉션 API를 통해 이벤트 목록을 수정하려 한다.
        // then: 수정 요청을 거부하고 최초 게임 목록을 유지한다.
        assertThatThrownBy { (event.gameIds as MutableList<Long>).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
        assertThat(event.gameIds).containsExactly(10L, 20L)
    }
}
