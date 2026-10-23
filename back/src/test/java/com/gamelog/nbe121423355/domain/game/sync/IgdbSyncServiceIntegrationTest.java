package com.gamelog.nbe121423355.domain.game.sync;

import com.gamelog.nbe121423355.domain.game.client.IgdbClient;
import com.gamelog.nbe121423355.domain.game.repository.GameRepository;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Worker transactions must commit independently; do not wrap these tests in @Transactional.
@SpringBootTest
@MySqlIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IgdbSyncServiceIntegrationTest {
    @Autowired IgdbSyncService service;
    @Autowired IgdbSyncStore store;
    @Autowired GameRepository games;
    @MockitoBean IgdbClient client;

    @Test
    void commitsAllPagesAndCompletesOnlyAfterEmptyPage() {
        when(client.fetchPage(eq(0L), isNull(), anyLong()))
                .thenReturn(List.of(IgdbSyncTest.game(10, 0L)));
        when(client.fetchPage(eq(10L), isNull(), anyLong()))
                .thenReturn(List.of(IgdbSyncTest.game(20, 0L)));
        when(client.fetchPage(eq(20L), isNull(), anyLong())).thenReturn(List.of());

        var started = service.start(1L);
        awaitStatus("SUCCEEDED");

        var completed = store.status();
        assertThat(completed.getCursorId()).isEqualTo(20L);
        assertThat(completed.getProcessed()).isEqualTo(2L);
        assertThat(completed.getInserted()).isEqualTo(2L);
        assertThat(completed.getWatermark()).isEqualTo(started.getCutoff());
        assertThat(games.findByIgdbId(10L)).isPresent();
        assertThat(games.findByIgdbId(20L)).isPresent();
        var calls = inOrder(client);
        calls.verify(client).fetchPage(0L, null, started.getCutoff());
        calls.verify(client).fetchPage(10L, null, started.getCutoff());
        calls.verify(client).fetchPage(20L, null, started.getCutoff());
        verifyNoMoreInteractions(client);
    }

    @Test
    void failedFetchPreservesCommittedPageAndResumesFromSavedCursor() {
        when(client.fetchPage(eq(0L), isNull(), anyLong()))
                .thenReturn(List.of(IgdbSyncTest.game(10, 0L)));
        when(client.fetchPage(eq(10L), isNull(), anyLong()))
                .thenThrow(new IllegalStateException("Simulated IGDB outage"))
                .thenReturn(List.of(IgdbSyncTest.game(20, 0L)));
        when(client.fetchPage(eq(20L), isNull(), anyLong())).thenReturn(List.of());

        var firstRun = service.start(1L);
        awaitStatus("FAILED");
        assertThat(store.status().getWatermark()).isNull();
        assertThat(store.status().getCursorId()).isEqualTo(10L);
        assertThat(games.findByIgdbId(10L)).isPresent();
        assertThat(games.findByIgdbId(20L)).isEmpty();

        var resumed = service.start(2L);
        awaitStatus("SUCCEEDED");

        assertThat(resumed.getRunId()).isNotEqualTo(firstRun.getRunId());
        assertThat(resumed.getCutoff()).isEqualTo(firstRun.getCutoff());
        assertThat(store.status().getInserted()).isEqualTo(2L);
        assertThat(store.status().getWatermark()).isEqualTo(firstRun.getCutoff());
        assertThat(games.count()).isEqualTo(2L);
        assertThat(games.findByIgdbId(20L)).isPresent();
        verify(client, times(1)).fetchPage(0L, null, firstRun.getCutoff());
        verify(client, times(2)).fetchPage(10L, null, firstRun.getCutoff());
        verify(client).fetchPage(20L, null, firstRun.getCutoff());
        verifyNoMoreInteractions(client);
    }

    private void awaitStatus(String status) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(store.status().getStatus()).isEqualTo(status));
    }
}
