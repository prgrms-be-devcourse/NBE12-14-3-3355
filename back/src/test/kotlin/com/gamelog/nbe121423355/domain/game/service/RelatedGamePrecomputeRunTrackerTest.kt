package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions

class RelatedGamePrecomputeRunTrackerTest {
    private lateinit var store: RelatedGamePrecomputeRunStore
    private lateinit var observer: RelatedGamePrecomputeObserver
    private lateinit var nanos: AtomicLong

    @BeforeEach
    fun setUp() {
        store = mock(RelatedGamePrecomputeRunStore::class.java)
        nanos = AtomicLong()
        observer = RelatedGamePrecomputeRunTracker(store).observer("run-1", nanos::get)
        observer.onAcquired()
        observer.onPrepared(1_000L, 1_000L)
        clearInvocations(store)
    }

    @Test
    fun savesProgressAfterOneHundredProcessedGames() {
        // given: 시간 경과 없이 99개의 성공 결과를 알린다.
        for (id in 1L..99L) {
            observer.onProcessed(id, progress(id, id, 0), null)
        }
        verifyNoInteractions(store)

        // when: 마지막 저장 이후 100번째 처리 결과를 알린다.
        val progress = progress(100, 100, 0)
        observer.onProcessed(100L, progress, null)

        // then: 100번째 처리 집계를 한 번 저장한다.
        verify(store).progress("run-1", progress)
        verifyNoMoreInteractions(store)
    }

    @Test
    fun savesProgressAtGameBoundaryAfterFiveSeconds() {
        // given: 마지막 저장 후 5초가 지난 시점을 준비한다.
        nanos.set(Duration.ofSeconds(5).toNanos())
        val progress = progress(1, 1, 0)

        // when: 다음 게임의 처리 완료를 알린다.
        observer.onProcessed(1L, progress, null)

        // then: 100개 미만이어도 시간 기준으로 집계를 저장한다.
        verify(store).progress("run-1", progress)
    }

    @Test
    fun savesFailureImmediatelyAndRestartsCheckpointFromSavedCounts() {
        // given: 건수·시간 기준 이전에 실패하는 첫 게임을 준비한다.
        val failure = IllegalStateException("계산 오류")
        val failed = progress(1, 0, 1)

        // when: 실패 뒤 성공 결과를 99개 더 알린다.
        observer.onProcessed(1L, failed, failure)
        for (id in 2L..100L) {
            observer.onProcessed(id, progress(id, id - 1, 1), null)
        }

        // then: 실패 집계는 즉시 저장하고 다음 체크포인트는 그 저장 이후부터 센다.
        verify(store).failure("run-1", 1L, failed, failure)
        verify(store, never()).progress(anyString(), any<RelatedGamePrecomputeObserver.Progress>() ?: progress(0, 0, 0))
        observer.onProcessed(101L, progress(101, 100, 1), null)
        verify(store).progress("run-1", progress(101, 100, 1))
    }

    @Test
    fun savesFinalCountsBeforeCheckpoint() {
        // given: 체크포인트 이전에 1개 게임이 성공한 상태를 준비한다.
        observer.onProcessed(1L, progress(1, 1, 0), null)
        verifyNoInteractions(store)

        // when: 배치의 정상 종료를 알린다.
        observer.onCompleted(
            RelatedGamePrecomputeBatchService.Result(
                acquired = true, processed = 1, succeeded = 1, failedIds = emptyList(), elapsed = Duration.ofSeconds(1),
            )
        )

        // then: 마지막 처리까지 포함한 최종 집계를 저장한다.
        verify(store).complete("run-1", progress(1, 1, 0))
    }

    @Test
    fun failedDetailRollbackDoesNotAdvanceFailureCount() {
        // given: 첫 성공 뒤 두 번째 실패의 상세 저장이 롤백되도록 준비한다.
        observer.onProcessed(1L, progress(1, 1, 0), null)
        val calculationFailure = IllegalStateException("계산 오류")
        val recordingFailure = IllegalStateException("실패 상세 저장 오류")
        doThrow(recordingFailure).`when`(store).failure(
                "run-1", 2L, progress(2, 1, 1), calculationFailure)

        // when: 실패 상세를 저장하지 못하고 전체 실행 오류를 기록한다.
        val failure = catchThrowable { observer.onProcessed(2L, progress(2, 1, 1), calculationFailure) }
        observer.onFailed(recordingFailure)

        // then: 상세가 없는 실패 건수를 집계에만 추가하지 않는다.
        assertThat(failure).isSameAs(recordingFailure)
        verify(store).fail("run-1", progress(1, 1, 0), recordingFailure)
    }

    private fun progress(processed: Long, succeeded: Long, failed: Long): RelatedGamePrecomputeObserver.Progress =
        RelatedGamePrecomputeObserver.Progress(processed, succeeded, failed)
}
