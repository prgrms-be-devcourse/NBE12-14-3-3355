package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.client.DeepLClient
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncTest
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus

class GameTranslationServiceTest {
    private val repository = mock(GameRepository::class.java)
    private val recorder = mock(RelatedGameChangeRecorder::class.java)
    private val client = mock(DeepLClient::class.java)
    private val manager = mock(PlatformTransactionManager::class.java)
    private val service = GameTranslationService(repository, recorder, client, manager, true, 1)

    @Test
    fun `원문 준비를 커밋한 뒤 외부 번역을 호출하고 결과를 별도 트랜잭션에 저장한다`() {
        // given: 저장된 게임과 외부 번역 응답 및 트랜잭션 경계를 준비한다.
        val game = prepareGame()
        `when`(repository.findById(10L)).thenReturn(Optional.of(game))
        `when`(client.translate("Summary")).thenReturn("한국어 설명")

        // when: 한 게임의 번역을 실행한다.
        val translated = service.translatePending()

        // then: 외부 호출 전후로 각각 커밋하고 원문과 번역 해시를 보존한다.
        val order = inOrder(manager, client)
        order.verify(manager).commit(any(TransactionStatus::class.java))
        order.verify(client).translate("Summary")
        order.verify(manager).commit(any(TransactionStatus::class.java))
        assertThat(translated).isEqualTo(1)
        assertThat(game.descriptionSource).isEqualTo("Summary")
        assertThat(game.description).isEqualTo("한국어 설명")
        assertThat(game.descriptionTranslatedHash).isEqualTo(GameTranslationService.hash("Summary"))
    }

    @Test
    fun `목록 조회 후 게임이 사라지면 기존 누락 예외를 유지하고 외부 번역을 호출하지 않는다`() {
        // given: 목록에는 있지만 재조회할 때 사라진 게임을 준비한다.
        prepareGame()
        `when`(repository.findById(10L)).thenReturn(Optional.empty())

        // when: 사라진 게임의 번역을 시도한다.
        val execution = { service.translatePending() }

        // then: 기존 Optional 조회와 같은 예외를 내고 외부 호출을 하지 않는다.
        assertThatThrownBy { execution() }
            .isInstanceOf(NoSuchElementException::class.java)
            .hasMessage("No value present")
        verifyNoInteractions(client)
    }

    private fun prepareGame(): Game {
        val game = Game.createFromIgdb(IgdbSyncTest.game(1L, 0L))
        ReflectionTestUtils.setField(game, "id", 10L)
        `when`(repository.findAll(any(Pageable::class.java))).thenReturn(PageImpl(listOf(game)))
        `when`(manager.getTransaction(any(TransactionDefinition::class.java)))
            .thenAnswer { SimpleTransactionStatus() }
        return game
    }
}
