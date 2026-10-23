package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun;
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver.Progress;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget;
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class RelatedGamePrecomputeRunTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 10, 0);

    @Test
    void capturesInputIdsAndRestoresNormalizedRequest() {
        // given: 중복·정렬 전 ID 목록으로 실행 범위를 만든다.
        var ids = new ArrayList<>(List.of(3L, 1L, 3L));
        var target = new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.IDS, ids);

        // when: 호출자의 원본 목록을 변경하고 관리자 이력을 생성한다.
        ids.clear();
        var run = createRun(target);

        // then: 접수한 범위를 보존하고 재실행용 ID는 중복 제거·정렬하여 복원한다.
        assertThat(target.normalizedIds()).containsExactly(1L, 3L);
        assertThat(run.target().getGameIds()).containsExactly(1L, 3L);
        assertThat(run.getRequestedGameIds()).isEqualTo("1,3");
    }

    @Test
    void restrictsAdminIdsWithoutRestrictingCliTarget() {
        // given: CLI에서도 사용할 수 있는 1,001개 지정 ID 대상을 준비한다.
        var ids = LongStream.rangeClosed(1, 1_001).boxed().toList();
        var target = new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.IDS, ids);

        // when: 같은 대상을 관리자 실행 이력으로 저장하려 한다.
        var failure = catchThrowable(() -> createRun(target));

        // then: 공통 대상 모델은 허용하되 관리자 경계에서 최대 개수를 검증한다.
        assertThat(target.normalizedIds()).hasSize(1_001);
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1,000");
    }

    @Test
    void rejectsIdsForAllOrEmptyIdsForSpecificExecution() {
        // given: ID가 허용되지 않는 실행과 비거나 양수가 아닌 지정 ID를 준비한다.
        var allIds = List.of(1L);
        List<Long> emptyIds = List.of();
        var invalidIds = List.of(0L);

        // when: 모순된 실행 범위를 생성한다.
        var allFailure = catchThrowable(() -> new RelatedGamePrecomputeTarget(
                RelatedGamePrecomputeTargetType.ALL, allIds));
        var emptyFailure = catchThrowable(() -> new RelatedGamePrecomputeTarget(
                RelatedGamePrecomputeTargetType.IDS, emptyIds));
        var invalidFailure = catchThrowable(() -> new RelatedGamePrecomputeTarget(
                RelatedGamePrecomputeTargetType.IDS, invalidIds));

        // then: 계산을 시작하기 전에 모든 잘못된 조합을 거절한다.
        assertThat(allFailure).isInstanceOf(IllegalArgumentException.class);
        assertThat(emptyFailure).isInstanceOf(IllegalArgumentException.class);
        assertThat(invalidFailure).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalResultCannotBeOverwrittenByLateProgress() {
        // given: 준비와 계산을 거쳐 정상 완료된 실행을 준비한다.
        var run = createRun(new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL, List.of()));
        run.start(NOW);
        run.prepared(10L, 10, NOW);
        run.complete(new Progress(10, 10, 0), NOW);

        // when: 이미 종료된 실행에 늦게 도착한 진행 정보를 반영하려 한다.
        var failure = catchThrowable(() -> run.record(new Progress(11, 11, 0), NOW));

        // then: 최종 상태·집계의 덮어쓰기를 거절한다.
        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(run.getStatus()).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED);
        assertThat(run.getProcessed()).isEqualTo(10);
    }

    @Test
    void progressMustBeConsistentAndMonotonic() {
        // given: 성공 2건을 기록한 실행을 준비한다.
        var run = createRun(new RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL, List.of()));
        run.start(NOW);
        run.prepared(10L, 10, NOW);
        run.record(new Progress(2, 2, 0), NOW);

        // when: 합계가 모순되거나 이전보다 감소한 집계를 전달한다.
        var inconsistent = catchThrowable(() -> new Progress(2, 1, 0));
        var decreasing = catchThrowable(() -> run.record(new Progress(1, 1, 0), NOW));

        // then: 처리=성공+실패와 누적 집계의 단조 증가 조건을 보존한다.
        assertThat(inconsistent).isInstanceOf(IllegalArgumentException.class);
        assertThat(decreasing).isInstanceOf(IllegalArgumentException.class);
    }

    private RelatedGamePrecomputeRun createRun(RelatedGamePrecomputeTarget target) {
        return new RelatedGamePrecomputeRun(1L, target, "test-owner", null, NOW);
    }
}
