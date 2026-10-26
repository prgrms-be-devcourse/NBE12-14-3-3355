package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.repository.GameRepository;
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeTargetRepository;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelatedGamePrecomputeBatchServiceTest {

    @Mock
    private GameRepository gameRepository;
    @Mock
    private RelatedGamePrecomputeTargetRepository targetRepository;
    @Mock
    private RelatedGamePrecomputeService precomputeService;
    @Mock
    private RelatedGamePrecomputeLock lock;
    @Mock
    private RelatedGamePrecomputeSnapshot snapshot;

    @Mock
    private RelatedGameChangePreparation changePreparation;
    @Mock
    private com.gamelog.nbe121423355.domain.game.repository.RelatedGameRefreshTargetRepository refreshTargets;

    private RelatedGamePrecomputeBatchService batch;

    @BeforeEach
    void setUp() {
        batch = new RelatedGamePrecomputeBatchService(gameRepository, precomputeService, lock, changePreparation,
                org.mockito.Mockito.mock(RelatedGameChangeExpansion.class), refreshTargets, targetRepository);
    }

    private void allowLock() {
        when(lock.tryRun(any())).thenAnswer(invocation -> {
            Function<Runnable, Object> action = invocation.getArgument(0);
            return Optional.of(action.apply(() -> {}));
        });
    }

    @Test
    void skipsAllWorkWhenAnotherExecutionOwnsLock() {
        // given: 다른 작업이 추천 실행 잠금을 보유한 상태를 준비한다.
        when(lock.tryRun(any())).thenReturn(Optional.empty());

        // when: 전체 계산을 요청한다.
        var result = batch.run(true, List.of(), RelatedGamePrecomputeMode.INITIAL_LOAD);

        // then: 잠금을 얻지 못하면 계산을 시작하지 않는다.
        assertThat(result.acquired()).isFalse();
        assertThat(result.exitCode()).isEqualTo(2);
        verifyNoInteractions(gameRepository, precomputeService);
    }

    @Test
    void continuesAfterIndividualFailureAndReturnsRetryIds() {
        // given: 중간 게임의 계산만 실패하도록 준비한다.
        allowLock();
        when(precomputeService.precompute(anyLong(), eq(RelatedGamePrecomputeMode.REFRESH)))
                .thenAnswer(invocation -> {
                    if (Long.valueOf(20).equals(invocation.getArgument(0))) {
                        throw new IllegalStateException("저장 실패");
                    }
                    return 20;
                });

        // when: 중복된 ID가 포함된 지정 계산을 실행한다.
        var result = batch.run(false, List.of(30L, 10L, 20L, 10L), RelatedGamePrecomputeMode.REFRESH);

        // then: 중복 제거·정렬을 유지하고 실패 뒤에도 다음 게임을 처리한다.

        assertThat(result.processed()).isEqualTo(3);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failedIds()).containsExactly(20L);
        assertThat(result.exitCode()).isEqualTo(1);
        verify(precomputeService, never()).prepare(any(), anyLong());
        var order = inOrder(precomputeService);
        order.verify(precomputeService).precompute(10L, RelatedGamePrecomputeMode.REFRESH);
        order.verify(precomputeService).precompute(20L, RelatedGamePrecomputeMode.REFRESH);
        order.verify(precomputeService).precompute(30L, RelatedGamePrecomputeMode.REFRESH);
    }

    @Test
    void allUsesKeysetPagesAndFixedUpperBoundary() {
        // given: 실행 시 최대 ID와 세 번의 페이지 조회 결과를 고정한다.
        allowLock();
        when(gameRepository.findMaxGameIdForPrecompute()).thenReturn(105L);
        when(precomputeService.prepare(RelatedGamePrecomputeMode.INITIAL_LOAD, 105L)).thenReturn(snapshot);
        when(gameRepository.findGameIdsForPrecompute(0, 105, PageRequest.of(0, 100)))
                .thenReturn(List.of(1L, 100L));
        when(gameRepository.findGameIdsForPrecompute(100, 105, PageRequest.of(0, 100)))
                .thenReturn(List.of(105L));
        when(gameRepository.findGameIdsForPrecompute(105, 105, PageRequest.of(0, 100)))
                .thenReturn(List.of());

        // when: 전체 계산을 요청한다.
        var result = batch.run(true, List.of(), RelatedGamePrecomputeMode.INITIAL_LOAD);

        // then: 시작 경계 안의 게임만 처리하고 최대 ID를 다시 읽지 않는다.
        assertThat(result.processed()).isEqualTo(3);
        assertThat(result.exitCode()).isZero();
        verify(precomputeService).precompute(105L, RelatedGamePrecomputeMode.INITIAL_LOAD, snapshot);
        verify(gameRepository, times(1)).findMaxGameIdForPrecompute();
        verifyNoMoreInteractions(gameRepository);
    }

    @Test
    void changedReusesGenreRankingAcrossPagesAndOnlyProcessesPendingTargets() {
        // given: 같은 장르 조합의 대기 게임 두 개와 대기가 아닌 후보 게임을 준비한다.
        allowLock();
        when(gameRepository.findMaxGameIdForPrecompute()).thenReturn(30L);
        when(refreshTargets.countPending(30L)).thenReturn(2L);
        when(refreshTargets.findPendingIds(0L, 30L, 100)).thenReturn(List.of(10L));
        when(refreshTargets.findPendingIds(10L, 30L, 100)).thenReturn(List.of(20L));
        when(refreshTargets.findPendingIds(20L, 30L, 100)).thenReturn(List.of());
        var shared = new RelatedGamePrecomputeSnapshot(RelatedGamePrecomputeMode.REFRESH, 20,
                Map.of(10L, List.of(1L), 20L, List.of(1L), 30L, List.of(1L)),
                Map.of(10L, BigDecimal.valueOf(80), 20L, BigDecimal.valueOf(80), 30L, BigDecimal.valueOf(80)), Set.of());
        when(precomputeService.prepare(RelatedGamePrecomputeMode.REFRESH, 30L)).thenReturn(shared);
        when(precomputeService.precompute(anyLong(), eq(RelatedGamePrecomputeMode.REFRESH), same(shared)))
                .thenAnswer(invocation -> shared.baselineFor(invocation.getArgument(0)).size());
        var observer = mock(RelatedGamePrecomputeObserver.class);

        // when: 변경 영향 갱신을 여러 페이지로 계산한다.
        var result = batch.run(new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.CHANGED),
                RelatedGamePrecomputeMode.REFRESH, observer);

        // then: 확장 후 한 번 준비한 스냅샷으로 장르 계산을 재사용하고 대기 게임만 처리한다.
        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(shared.calculatedGroupCount()).isEqualTo(1);
        var order = inOrder(changePreparation, refreshTargets, precomputeService, observer);
        order.verify(changePreparation).expandPending(any());
        order.verify(refreshTargets).countPending(30L);
        order.verify(precomputeService).prepare(RelatedGamePrecomputeMode.REFRESH, 30L);
        order.verify(observer).onPrepared(30L, 2L);
        verify(precomputeService).precompute(10L, RelatedGamePrecomputeMode.REFRESH, shared);
        verify(precomputeService).precompute(20L, RelatedGamePrecomputeMode.REFRESH, shared);
        verify(precomputeService, never()).precompute(eq(30L), any(), any());
        verify(precomputeService, never()).precompute(anyLong(), any());
        verify(gameRepository, never()).findGameIdsForPrecompute(anyLong(), anyLong(), any());
    }

    @Test
    void changedWithoutPendingTargetsSkipsSnapshotPreparation() {
        // given: 영향 대상을 확장한 뒤에도 대기가 없는 실행을 준비한다.
        allowLock();
        when(gameRepository.findMaxGameIdForPrecompute()).thenReturn(30L);
        when(refreshTargets.countPending(30L)).thenReturn(0L);
        var observer = mock(RelatedGamePrecomputeObserver.class);

        // when: 대상 없는 변경 영향 갱신을 실행한다.
        var result = batch.run(new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.CHANGED),
                RelatedGamePrecomputeMode.REFRESH, observer);

        // then: 예상 대상 0개로 정상 종료하고 공통 데이터와 개별 계산을 준비하지 않는다.
        assertThat(result.processed()).isZero();
        assertThat(result.exitCode()).isZero();
        verify(changePreparation).expandPending(any());
        verify(observer).onPrepared(30L, 0L);
        verifyNoInteractions(precomputeService);
    }

    @Test
    void lostLockStopsExecutionInsteadOfTreatingItAsIndividualGameFailure() {
        // given: 첫 게임 처리 직후 잠금 연결 소유권을 잃도록 준비한다.
        when(lock.tryRun(any())).thenAnswer(invocation -> {
            Function<Runnable, Object> action = invocation.getArgument(0);
            AtomicInteger checks = new AtomicInteger();
            Runnable check = () -> {
                if (checks.incrementAndGet() > 1) {
                    throw new IllegalStateException("잠금 연결 단절");
                }
            };
            return Optional.of(action.apply(check));
        });

        // when: 잠금 연결이 끊긴 상태에서 다음 게임으로 진행하려 한다.
        var failure = catchThrowable(() -> batch.run(false, List.of(1L, 2L), RelatedGamePrecomputeMode.REFRESH));

        // then: 개별 게임 실패로 삼키지 않고 실행 전체를 중단한다.
        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage("잠금 연결 단절");

        verify(precomputeService).precompute(1L, RelatedGamePrecomputeMode.REFRESH);
        verify(precomputeService, never()).precompute(2L, RelatedGamePrecomputeMode.REFRESH);
    }

    @Test
    void retriesFailedTargetsWithFixedUpperBoundary() {
        // given: 실패 대상 2개와 고정된 ID 상한을 준비한다.
        allowLock();
        when(gameRepository.findMaxGameIdForPrecompute()).thenReturn(30L);
        when(targetRepository.count(RelatedGamePrecomputeTargetType.FAILED, 30L)).thenReturn(2L);
        when(targetRepository.findIds(RelatedGamePrecomputeTargetType.FAILED, 0L, 30L, 100))
                .thenReturn(List.of(10L, 20L));
        when(targetRepository.findIds(RelatedGamePrecomputeTargetType.FAILED, 20L, 30L, 100))
                .thenReturn(List.of());

        // when: 실패 게임만 대상으로 갱신을 실행한다.
        var result = batch.run(new RelatedGamePrecomputeTarget(
                        RelatedGamePrecomputeTargetType.FAILED, List.of()),
                RelatedGamePrecomputeMode.REFRESH, RelatedGamePrecomputeObserver.NONE);

        // then: 선정된 게임만 개별 계산하고 전체 스냅샷은 준비하지 않는다.
        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.exitCode()).isZero();
        verify(precomputeService).precompute(10L, RelatedGamePrecomputeMode.REFRESH);
        verify(precomputeService).precompute(20L, RelatedGamePrecomputeMode.REFRESH);
        verify(precomputeService, never()).prepare(any(), anyLong());
    }

    @Test
    void observerFailureStopsBatchWithoutTreatingItAsGameFailure() {
        // given: 첫 게임 계산 이후 이력 저장이 실패하도록 준비한다.
        allowLock();
        var observer = mock(RelatedGamePrecomputeObserver.class);
        var recordingFailure = new IllegalStateException("이력 저장 실패");
        doThrow(recordingFailure).when(observer).onProcessed(eq(1L),
                any(RelatedGamePrecomputeObserver.Progress.class), isNull());

        // when: 두 게임을 실행하다 첫 번째 결과의 이력 저장이 실패한다.
        var failure = catchThrowable(() -> batch.run(new RelatedGamePrecomputeTarget(
                        RelatedGamePrecomputeTargetType.IDS, List.of(1L, 2L)),
                RelatedGamePrecomputeMode.REFRESH, observer));

        // then: 원래 오류를 전달하고 다음 계산과 정상 완료 통지는 수행하지 않는다.
        assertThat(failure).isSameAs(recordingFailure);
        verify(precomputeService).precompute(1L, RelatedGamePrecomputeMode.REFRESH);
        verify(precomputeService, never()).precompute(2L, RelatedGamePrecomputeMode.REFRESH);
        verify(observer).onFailed(recordingFailure);
        verify(observer, never()).onCompleted(any());
    }

    @Test
    void rejectedExecutionNotifiesObserverWithoutPreparingTargets() {
        // given: 다른 실행이 잠금을 보유한 상태와 관리자 진행 통지 대상을 준비한다.
        when(lock.tryRun(any())).thenReturn(Optional.empty());
        var observer = mock(RelatedGamePrecomputeObserver.class);

        // when: 관리자 전체 갱신을 요청한다.
        var result = batch.run(new RelatedGamePrecomputeTarget(
                        RelatedGamePrecomputeTargetType.ALL, List.of()),
                RelatedGamePrecomputeMode.REFRESH, observer);

        // then: 거절만 알리고 대상 조회·계산을 시작하지 않는다.
        assertThat(result.acquired()).isFalse();
        verify(observer).onRejected();
        verify(observer, never()).onAcquired();
        verifyNoInteractions(gameRepository, targetRepository, precomputeService);
    }

    @Test
    void keepsOriginalFailureWhenFailureReportingAlsoFails() {
        // given: 진행 기록과 전체 실패 기록이 연달아 실패하도록 준비한다.
        allowLock();
        var observer = mock(RelatedGamePrecomputeObserver.class);
        var original = new IllegalStateException("진행 기록 오류");
        var reporting = new IllegalArgumentException("실패 기록 오류");
        doThrow(original).when(observer).onProcessed(anyLong(), any(), isNull());
        doThrow(reporting).when(observer).onFailed(original);

        // when: 계산 성공 뒤 이력 기록과 실패 통지가 모두 실패한다.
        var failure = catchThrowable(() -> batch.run(new RelatedGamePrecomputeTarget(
                        RelatedGamePrecomputeTargetType.IDS, List.of(1L)),
                RelatedGamePrecomputeMode.REFRESH, observer));

        // then: 처음 오류를 유지하고 후속 기록 오류를 suppressed로 보존한다.
        assertThat(failure).isSameAs(original)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(reporting));
    }
}
