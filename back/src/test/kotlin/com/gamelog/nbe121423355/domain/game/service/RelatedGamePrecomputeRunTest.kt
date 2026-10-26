package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRunStatus
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver.Progress
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

class RelatedGamePrecomputeRunTest {
    private val now = LocalDateTime.of(2026, 10, 23, 10, 0)

    @Test
    fun capturesInputIdsAndRestoresNormalizedRequest() {
        // given: 중복·정렬 전 ID 목록으로 실행 범위를 만든다.
        val ids = mutableListOf(3L, 1L, 3L)
        val target = RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.IDS, ids)

        // when: 호출자의 원본 목록을 변경하고 관리자 이력을 생성한다.
        ids.clear()
        val run = createRun(target)

        // then: 접수한 범위를 보존하고 재실행용 ID는 중복 제거·정렬하여 복원한다.
        assertThat(target.normalizedIds()).containsExactly(1L, 3L)
        assertThat(run.target().gameIds).containsExactly(1L, 3L)
        assertThat(run.requestedGameIds).isEqualTo("1,3")
    }

    @Test
    fun restrictsAdminIdsWithoutRestrictingCliTarget() {
        // given: CLI에서도 사용할 수 있는 1,001개 지정 ID 대상을 준비한다.
        val ids = (1L..1_001L).toList()
        val target = RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.IDS, ids)

        // when: 같은 대상을 관리자 실행 이력으로 저장하려 한다.
        val failure = catchThrowable { createRun(target) }

        // then: 공통 대상 모델은 허용하되 관리자 경계에서 최대 개수를 검증한다.
        assertThat(target.normalizedIds()).hasSize(1_001)
        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("1,000")
    }

    @Test
    fun rejectsIdsForAllOrEmptyIdsForSpecificExecution() {
        // given: ID가 허용되지 않는 실행과 비거나 양수가 아닌 지정 ID를 준비한다.
        val allIds = listOf(1L)
        val emptyIds = emptyList<Long>()
        val invalidIds = listOf(0L)

        // when: 모순된 실행 범위를 생성한다.
        val allFailure = catchThrowable {
            RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL, allIds)
        }
        val emptyFailure = catchThrowable {
            RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.IDS, emptyIds)
        }
        val invalidFailure = catchThrowable {
            RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.IDS, invalidIds)
        }

        // then: 계산을 시작하기 전에 모든 잘못된 조합을 거절한다.
        assertThat(allFailure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(emptyFailure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(invalidFailure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun terminalResultCannotBeOverwrittenByLateProgress() {
        // given: 준비와 계산을 거쳐 정상 완료된 실행을 준비한다.
        val run = createRun(RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL, emptyList()))
        run.start(now)
        run.prepared(10L, 10, now)
        run.complete(Progress(10, 10, 0), now)

        // when: 이미 종료된 실행에 늦게 도착한 진행 정보를 반영하려 한다.
        val failure = catchThrowable { run.record(Progress(11, 11, 0), now) }

        // then: 최종 상태·집계의 덮어쓰기를 거절한다.
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(run.status).isEqualTo(RelatedGamePrecomputeRunStatus.SUCCEEDED)
        assertThat(run.processed).isEqualTo(10)
    }

    @Test
    fun progressMustBeConsistentAndMonotonic() {
        // given: 성공 2건을 기록한 실행을 준비한다.
        val run = createRun(RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL, emptyList()))
        run.start(now)
        run.prepared(10L, 10, now)
        run.record(Progress(2, 2, 0), now)

        // when: 합계가 모순되거나 이전보다 감소한 집계를 전달한다.
        val inconsistent = catchThrowable { Progress(2, 1, 0) }
        val decreasing = catchThrowable { run.record(Progress(1, 1, 0), now) }

        // then: 처리=성공+실패와 누적 집계의 단조 증가 조건을 보존한다.
        assertThat(inconsistent).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(decreasing).isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun createRun(target: RelatedGamePrecomputeTarget): RelatedGamePrecomputeRun =
        RelatedGamePrecomputeRun(1L, target, "test-owner", null, now)
}
