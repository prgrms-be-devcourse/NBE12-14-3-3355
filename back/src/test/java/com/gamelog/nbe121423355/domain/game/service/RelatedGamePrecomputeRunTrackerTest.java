package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RelatedGamePrecomputeRunTrackerTest {
    private RelatedGamePrecomputeRunStore store;
    private RelatedGamePrecomputeObserver observer;
    private AtomicLong nanos;

    @BeforeEach
    void setUp() {
        store = mock(RelatedGamePrecomputeRunStore.class);
        nanos = new AtomicLong();
        observer = new RelatedGamePrecomputeRunTracker(store).observer("run-1", nanos::get);
        observer.onAcquired();
        observer.onPrepared(1_000L, 1_000L);
        clearInvocations(store);
    }

    @Test
    void savesProgressAfterOneHundredProcessedGames() {
        // given: 시간 경과 없이 99개의 성공 결과를 알린다.
        for (long id = 1; id <= 99; id++) {
            observer.onProcessed(id, progress(id, id, 0), null);
        }
        verifyNoInteractions(store);

        // when: 마지막 저장 이후 100번째 처리 결과를 알린다.
        var progress = progress(100, 100, 0);
        observer.onProcessed(100L, progress, null);

        // then: 100번째 처리 집계를 한 번 저장한다.
        verify(store).progress("run-1", progress);
        verifyNoMoreInteractions(store);
    }

    @Test
    void savesProgressAtGameBoundaryAfterFiveSeconds() {
        // given: 마지막 저장 후 5초가 지난 시점을 준비한다.
        nanos.set(Duration.ofSeconds(5).toNanos());
        var progress = progress(1, 1, 0);

        // when: 다음 게임의 처리 완료를 알린다.
        observer.onProcessed(1L, progress, null);

        // then: 100개 미만이어도 시간 기준으로 집계를 저장한다.
        verify(store).progress("run-1", progress);
    }

    @Test
    void savesFailureImmediatelyAndRestartsCheckpointFromSavedCounts() {
        // given: 건수·시간 기준 이전에 실패하는 첫 게임을 준비한다.
        var failure = new IllegalStateException("계산 오류");
        var failed = progress(1, 0, 1);

        // when: 실패 뒤 성공 결과를 99개 더 알린다.
        observer.onProcessed(1L, failed, failure);
        for (long id = 2; id <= 100; id++) {
            observer.onProcessed(id, progress(id, id - 1, 1), null);
        }

        // then: 실패 집계는 즉시 저장하고 다음 체크포인트는 그 저장 이후부터 센다.
        verify(store).failure("run-1", 1L, failed, failure);
        verify(store, never()).progress(anyString(), any());
        observer.onProcessed(101L, progress(101, 100, 1), null);
        verify(store).progress("run-1", progress(101, 100, 1));
    }

    @Test
    void savesFinalCountsBeforeCheckpoint() {
        // given: 체크포인트 이전에 1개 게임이 성공한 상태를 준비한다.
        observer.onProcessed(1L, progress(1, 1, 0), null);
        verifyNoInteractions(store);

        // when: 배치의 정상 종료를 알린다.
        observer.onCompleted(new RelatedGamePrecomputeBatchService.Result(
                true, 1, 1, List.of(), Duration.ofSeconds(1)));

        // then: 마지막 처리까지 포함한 최종 집계를 저장한다.
        verify(store).complete("run-1", progress(1, 1, 0));
    }

    @Test
    void failedDetailRollbackDoesNotAdvanceFailureCount() {
        // given: 첫 성공 뒤 두 번째 실패의 상세 저장이 롤백되도록 준비한다.
        observer.onProcessed(1L, progress(1, 1, 0), null);
        var calculationFailure = new IllegalStateException("계산 오류");
        var recordingFailure = new IllegalStateException("실패 상세 저장 오류");
        doThrow(recordingFailure).when(store).failure(
                "run-1", 2L, progress(2, 1, 1), calculationFailure);

        // when: 실패 상세를 저장하지 못하고 전체 실행 오류를 기록한다.
        assertThatThrownBy(() -> observer.onProcessed(
                2L, progress(2, 1, 1), calculationFailure)).isSameAs(recordingFailure);
        observer.onFailed(recordingFailure);

        // then: 상세가 없는 실패 건수를 집계에만 추가하지 않는다.
        verify(store).fail("run-1", progress(1, 1, 0), recordingFailure);
    }

    private RelatedGamePrecomputeObserver.Progress progress(long processed, long succeeded, long failed) {
        return new RelatedGamePrecomputeObserver.Progress(processed, succeeded, failed);
    }
}
