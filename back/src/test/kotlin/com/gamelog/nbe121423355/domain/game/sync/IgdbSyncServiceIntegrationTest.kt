package com.gamelog.nbe121423355.domain.game.sync

import com.gamelog.nbe121423355.domain.game.client.IgdbClient
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.bean.override.mockito.MockitoBean

// 비동기 worker의 실제 커밋을 검증하므로 테스트 전체에 @Transactional을 적용하지 않는다.
@SpringBootTest
@MySqlIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IgdbSyncServiceIntegrationTest {
    @Autowired private lateinit var service: IgdbSyncService
    @Autowired private lateinit var store: IgdbSyncStore
    @Autowired private lateinit var games: GameRepository
    @MockitoBean private lateinit var client: IgdbClient

    @Test
    fun commitsAllPagesAndCompletesOnlyAfterEmptyPage() {
        given(client.fetchPage(eq(0L), isNull(), anyLong()))
            .willReturn(mutableListOf(IgdbSyncTest.game(10, 0L)))
        given(client.fetchPage(eq(10L), isNull(), anyLong()))
            .willReturn(mutableListOf(IgdbSyncTest.game(20, 0L)))
        given(client.fetchPage(eq(20L), isNull(), anyLong())).willReturn(mutableListOf())

        val started = service.start(1L)
        awaitStatus("SUCCEEDED")

        val completed = store.status()
        assertThat(completed.cursorId).isEqualTo(20L)
        assertThat(completed.processed).isEqualTo(2L)
        assertThat(completed.inserted).isEqualTo(2L)
        assertThat(completed.watermark).isEqualTo(started.cutoff)
        assertThat(games.findByIgdbId(10L)).isNotNull()
        assertThat(games.findByIgdbId(20L)).isNotNull()
        val calls = inOrder(client)
        calls.verify(client).fetchPage(0L, null, started.cutoff)
        calls.verify(client).fetchPage(10L, null, started.cutoff)
        calls.verify(client).fetchPage(20L, null, started.cutoff)
        verifyNoMoreInteractions(client)
    }

    @Test
    fun failedFetchPreservesCommittedPageAndResumesFromSavedCursor() {
        given(client.fetchPage(eq(0L), isNull(), anyLong()))
            .willReturn(mutableListOf(IgdbSyncTest.game(10, 0L)))
        given(client.fetchPage(eq(10L), isNull(), anyLong()))
            .willThrow(IllegalStateException("Simulated IGDB outage"))
            .willReturn(mutableListOf(IgdbSyncTest.game(20, 0L)))
        given(client.fetchPage(eq(20L), isNull(), anyLong())).willReturn(mutableListOf())

        val firstRun = service.start(1L)
        awaitStatus("FAILED")
        assertThat(store.status().watermark).isNull()
        assertThat(store.status().cursorId).isEqualTo(10L)
        assertThat(games.findByIgdbId(10L)).isNotNull()
        assertThat(games.findByIgdbId(20L)).isNull()

        val resumed = service.start(2L)
        awaitStatus("SUCCEEDED")

        assertThat(requireNotNull(resumed.runId)).isNotEqualTo(requireNotNull(firstRun.runId))
        assertThat(resumed.cutoff).isEqualTo(firstRun.cutoff)
        assertThat(store.status().inserted).isEqualTo(2L)
        assertThat(store.status().watermark).isEqualTo(firstRun.cutoff)
        assertThat(games.count()).isEqualTo(2L)
        assertThat(games.findByIgdbId(20L)).isNotNull()
        verify(client, times(1)).fetchPage(0L, null, firstRun.cutoff)
        verify(client, times(2)).fetchPage(10L, null, firstRun.cutoff)
        verify(client).fetchPage(20L, null, firstRun.cutoff)
        verifyNoMoreInteractions(client)
    }

    private fun awaitStatus(status: String) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(store.status().status).isEqualTo(status)
        }
    }
}
