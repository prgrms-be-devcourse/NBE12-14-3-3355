package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.dto.*;
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeTargetRepository;
import com.gamelog.nbe121423355.domain.game.service.model.*;
import com.gamelog.nbe121423355.global.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminRelatedGamePrecomputeServiceTest {
    @Mock RelatedGamePrecomputeTargetRepository targets;
    @Mock RelatedGamePrecomputeRunStore store;
    @Mock RelatedGamePrecomputeRunQueryService query;
    @Mock RelatedGamePrecomputeBatchService batch;
    @Mock RelatedGamePrecomputeRunTracker tracker;
    @Mock RelatedGamePrecomputeLock lock;
    @Mock RelatedGamePrecomputeRecoveryService recovery;
    private AdminRelatedGamePrecomputeService service;

    @BeforeEach
    void setUp() {
        service = new AdminRelatedGamePrecomputeService(targets, store, query, batch, tracker, lock, recovery,
                new RelatedGamePrecomputeAdminProperties());
    }

    @AfterEach
    void stopWorker() { service.stop(); }

    @Test
    void invalidIdRejectsWholeRequestBeforeDatabaseWrite() {
        // given: 양수 ID 사이에 잘못된 문자열을 포함한다.
        var request = request(RelatedGamePrecomputeTargetType.IDS, "1, invalid 2", null);

        // when: 지정 ID 실행을 접수한다.
        var error = catchThrowable(() -> service.start(7, request));

        // then: 오류 토큰을 안내하고 이력 생성·계산은 수행하지 않는다.
        assertThat(error).isInstanceOf(ServiceException.class).hasMessageContaining("invalid");
        verifyNoInteractions(targets, store, batch);
    }

    @Test
    void missingIdRejectsWholeRequest() {
        // given: 요청한 두 게임 중 하나만 DB에 존재한다.
        when(targets.findExistingIds(List.of(1L, 2L))).thenReturn(List.of(1L));

        // when: 두 게임의 계산을 요청한다.
        var error = catchThrowable(() -> service.start(7, request(RelatedGamePrecomputeTargetType.IDS, "1,2", null)));

        // then: 누락 ID를 안내하며 존재하는 게임도 부분 실행하지 않는다.
        assertThat(error).isInstanceOf(ServiceException.class).hasMessageContaining("2");
        verifyNoInteractions(store, batch);
    }

    @Test
    void rejectsMoreThanOneThousandNormalizedIdsBeforeQuery() {
        // given: 중복 제거 이후에도 1,001개인 ID 입력을 준비한다.
        String ids = LongStream.rangeClosed(1, 1_001).mapToObj(value -> Long.toString(value)).collect(Collectors.joining(","));

        // when: 지정 ID 계산을 접수한다.
        var error = catchThrowable(() -> service.start(7, request(RelatedGamePrecomputeTargetType.IDS, ids, null)));

        // then: 개수 제한을 확인하고 불필요한 DB 조회도 수행하지 않는다.
        assertThat(error).isInstanceOf(ServiceException.class).hasMessageContaining("1,000");
        verifyNoInteractions(targets, store, batch);
    }

    @Test
    void normalizesSeparatorsAndAlwaysUsesRefresh() throws Exception {
        // given: 중복·쉼표·공백·줄바꿈을 포함한 입력과 실행 기반을 준비한다.
        when(targets.findExistingIds(List.of(1L, 2L, 3L))).thenReturn(List.of(1L, 2L, 3L));
        var accepted = mock(AdminRelatedGamePrecomputeRunResponse.class);
        when(store.create(eq(7L), any(), anyString(), isNull())).thenReturn("run-1");
        when(query.detail("run-1")).thenReturn(accepted);
        when(tracker.observer("run-1")).thenReturn(RelatedGamePrecomputeObserver.NONE);
        var finished = new CountDownLatch(1);
        when(batch.run(any(RelatedGamePrecomputeTarget.class), eq(RelatedGamePrecomputeMode.REFRESH), any()))
                .thenAnswer(invocation -> {
                    finished.countDown();
                    return new RelatedGamePrecomputeBatchService.Result(true, 3, 3, List.of(), Duration.ZERO);
                });

        // when: 관리자 요청을 비동기로 접수한다.
        var response = service.start(7, request(RelatedGamePrecomputeTargetType.IDS, "3, 1\n2 1", null));
        boolean executed = finished.await(3, TimeUnit.SECONDS);

        // then: 접수 응답을 반환하고 정규화한 ID와 REFRESH만 배치로 전달한다.
        assertThat(response).isSameAs(accepted);
        assertThat(executed).isTrue();
        var target = ArgumentCaptor.forClass(RelatedGamePrecomputeTarget.class);
        verify(batch).run(target.capture(), eq(RelatedGamePrecomputeMode.REFRESH), any());
        assertThat(target.getValue().getGameIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void externalLockRejectsAdmissionWithoutCreatingHistory() {
        // given: CLI 또는 다른 서버가 공통 잠금을 보유한 상태다.
        when(lock.isLocked()).thenReturn(true);

        // when: 전체 갱신을 접수한다.
        var error = catchThrowable(() -> service.start(7, request(RelatedGamePrecomputeTargetType.ALL, null, null)));

        // then: 409로 거절하고 새 이력·계산을 시작하지 않는다.
        assertThat(error).isInstanceOfSatisfying(ServiceException.class,
                e -> assertThat(e.getResultCode()).isEqualTo("409-10"));
        verifyNoInteractions(store, batch);
    }

    @Test
    void retryCannotChangeOriginalExecutionTypeOrIds() {
        // given: 원본은 게임 1 실행이며 새 요청의 게임 2도 DB에는 존재한다.
        when(targets.findExistingIds(List.of(2L))).thenReturn(List.of(2L));
        when(query.replayTarget("source")).thenReturn(new RelatedGamePrecomputeTarget(
                RelatedGamePrecomputeTargetType.IDS, List.of(1L)));

        // when: 원본 실행을 연결하면서 다른 게임을 지정한다.
        var error = catchThrowable(() -> service.start(7, request(RelatedGamePrecomputeTargetType.IDS, "2", "source")));

        // then: 원본과 다른 재실행을 거절하고 새 이력을 만들지 않는다.
        assertThat(error).isInstanceOf(ServiceException.class).hasMessageContaining("원본 실행");
        verifyNoInteractions(store, batch);
    }


    @Test
    void freshRemoteAdmissionPreventsExecutionBeforeLockAcquisition() {
        // given: 다른 서버가 아직 잠금 획득 전이지만 정상 접수 이력을 유지하고 있다.
        when(query.hasRecentActiveRun()).thenReturn(true);

        // when: 같은 DB에 추가 관리자 실행을 요청한다.
        var error = catchThrowable(() -> service.start(7, request(RelatedGamePrecomputeTargetType.ALL, null, null)));

        // then: 현재 잠금이 비어 있어도 살아 있는 접수를 존중하고 추가 이력을 만들지 않는다.
        assertThat(error).isInstanceOfSatisfying(ServiceException.class,
                e -> assertThat(e.getResultCode()).isEqualTo("409-10"));
        verifyNoInteractions(store, batch);
    }

    @Test
    void executorShutdownAfterAdmissionRecordsRejectedRun() {
        // given: 이력 생성 직후 서버 종료로 worker 제출이 거절되는 상황을 준비한다.
        var accepted = mock(AdminRelatedGamePrecomputeRunResponse.class);
        when(store.create(eq(7L), any(), anyString(), isNull())).thenReturn("run-1");
        when(query.detail("run-1")).thenAnswer(invocation -> {
            service.stop();
            return accepted;
        });

        // when: 이력을 생성했지만 worker에는 전달할 수 없는 실행을 접수한다.
        var error = catchThrowable(() -> service.start(7, request(RelatedGamePrecomputeTargetType.ALL, null, null)));

        // then: ACCEPTED 이력을 방치하지 않고 실행 거절로 정리하며 409를 반환한다.
        assertThat(error).isInstanceOfSatisfying(ServiceException.class,
                e -> assertThat(e.getResultCode()).isEqualTo("409-10"));
        verify(store).reject("run-1");
        verifyNoInteractions(batch);
    }

    private AdminRelatedGamePrecomputeRequest request(RelatedGamePrecomputeTargetType type, String ids, String source) {
        return new AdminRelatedGamePrecomputeRequest(type, ids, source);
    }
}
