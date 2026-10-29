package com.gamelog.nbe121423355.domain.game.entity

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IgdbGameMappingTest {
    @Test
    fun `개발사 이름은 개발 참여 여부와 빈 이름을 걸러 중복 없이 원래 순서로 합친다`() {
        // given: 비개발사, 누락된 회사, 중복 이름과 Java 기준 공백이 아닌 이름을 준비한다.
        val response = IgdbSyncTest.game(10, 0L).copy(involved_companies = listOf(
            company(false, "Publisher"), company(null, "Unknown"),
            IgdbGameResponse.InvolvedCompany(1L, true, null),
            company(true, null), company(true, "  "),
            company(true, "Studio"), company(true, "Studio"), company(true, "\u00a0"),
        ))

        // when: IGDB 응답으로 게임을 만든다.
        val game = Game.createFromIgdb(response)

        // then: 기존 Java 공백 판정과 첫 등장 순서를 유지해 개발사 이름을 합친다.
        assertThat(game.developer).isEqualTo("Studio, \u00a0")
    }

    @Test
    fun `사용할 개발사가 없으면 개발사 정보를 비운다`() {
        // given: 개발사 정보가 있는 게임과 개발사 목록이 빠진 갱신 응답을 준비한다.
        val response = IgdbSyncTest.game(10, 0L)
        val game = Game.createFromIgdb(response.copy(involved_companies = listOf(company(true, "Studio"))))

        // when: 개발사 목록이 없는 응답으로 갱신한다.
        game.updateFromIgdb(response)

        // then: 이전 개발사 이름을 남기지 않는다.
        assertThat(game.developer).isNull()
    }

    private fun company(developer: Boolean?, name: String?) =
        IgdbGameResponse.InvolvedCompany(1L, developer, IgdbGameResponse.Company(1L, name))
}
