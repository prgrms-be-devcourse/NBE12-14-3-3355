package com.gamelog.nbe121423355.domain.game.entity

import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class RelatedGameCalculationTest {
    private val firstAttempt = LocalDateTime.of(2026, 10, 21, 10, 0)

    @Test
    fun initialFailureRemainsUnreadyAndCanBeRetried() {
        // given: 최초 계산 전인 상태를 준비하고 아직 공개 준비가 되지 않았음을 확인한다.
        val calculation = pendingCalculation()
        assertThat(calculation.isReady).isFalse()

        // when: 최초 실패를 기록한 뒤 다시 계산하여 성공한다.
        calculation.fail(firstAttempt, "DB 연결 실패")
        val readyAfterFailure = calculation.isReady
        val statusAfterFailure = calculation.lastAttemptStatus
        calculation.start(firstAttempt.plusMinutes(1))
        calculation.succeed(firstAttempt.plusMinutes(2))

        // then: 최초 실패에서는 미준비·실패 상태이고 재시도 성공 후에는 오류 없이 준비된다.
        assertThat(readyAfterFailure).isFalse()
        assertThat(statusAfterFailure).isEqualTo(RelatedGameCalculationStatus.FAILED)
        assertThat(calculation.isReady).isTrue()
        assertThat(calculation.lastError).isNull()
    }

    @Test
    fun failedRefreshPreservesPreviousSuccessAndReadiness() {
        // given: 이전 계산에 성공한 게임과 그 성공 시각을 준비한다.
        val calculation = pendingCalculation()
        calculation.start(firstAttempt)
        val succeededAt = firstAttempt.plusMinutes(1)
        calculation.succeed(succeededAt)

        // when: 다음 날 갱신을 시작한 뒤 저장 실패를 기록한다.
        calculation.start(firstAttempt.plusDays(1))
        val readyDuringRefresh = calculation.isReady
        calculation.fail(firstAttempt.plusDays(1), "저장 실패")

        // then: 갱신 중·실패 후에도 이전 성공과 준비 완료 상태를 유지한다.
        assertThat(readyDuringRefresh).isTrue()
        assertThat(calculation.isReady).isTrue()
        assertThat(calculation.lastSuccessAt).isEqualTo(succeededAt)
        assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.FAILED)
        assertThat(calculation.lastError).isEqualTo("저장 실패")
    }

    @Test
    fun successfulCalculationDoesNotRequireAnyCandidate() {
        // given: 후보를 저장하지 않은 대기 상태를 준비한다.
        val calculation = pendingCalculation()

        // when: 계산을 시작하고 후보 없이 성공을 기록한다.
        calculation.start(firstAttempt)
        calculation.succeed(firstAttempt.plusSeconds(1))

        // then: 빈 결과도 성공한 준비 완료 상태로 취급한다.
        assertThat(calculation.isReady).isTrue()
        assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.SUCCESS)
    }

    @Test
    fun longErrorDoesNotPreventFailureRecording() {
        // given: 대기 상태와 저장 길이를 넘는 오류 메시지를 준비한다.
        val calculation = pendingCalculation()
        val error = "x".repeat(1000)

        // when: 긴 오류 메시지로 최초 실패를 기록한다.
        calculation.fail(firstAttempt, error)

        // then: 오류를 500자로 제한하고 미준비 상태를 유지한다.
        assertThat(calculation.lastError).hasSize(500)
        assertThat(calculation.isReady).isFalse()
    }

    @Test
    fun pendingCalculationCannotBeMarkedSuccessfulWithoutStarting() {
        // given: 계산을 시작하지 않은 대기 상태를 준비한다.
        val calculation = pendingCalculation()

        // when: 시작 없이 성공 상태로 변경하려 한다.
        val failure = catchThrowable { calculation.succeed(firstAttempt) }

        // then: 잘못된 상태 전이를 거절하고 미준비 상태를 유지한다.
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(calculation.isReady).isFalse()
    }

    @Test
    fun nullFailureMessagePreservesPreviousSuccess() {
        // given: 정상 계산을 마친 상태와 마지막 성공 시각을 준비한다.
        val calculation = pendingCalculation()
        calculation.start(firstAttempt)
        val succeededAt = firstAttempt.plusMinutes(1)
        calculation.succeed(succeededAt)

        // when: 오류 메시지가 없는 재갱신 실패를 기록한다.
        calculation.fail(firstAttempt.plusDays(1), null)

        // then: 오류 메시지의 null과 이전 정상 결과의 준비 완료 상태를 유지한다.
        assertThat(calculation.lastError).isNull()
        assertThat(calculation.lastSuccessAt).isEqualTo(succeededAt)
        assertThat(calculation.isReady).isTrue()
        assertThat(calculation.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.FAILED)
    }

    private fun pendingCalculation(): RelatedGameCalculation {
        val game = mock(Game::class.java)
        `when`(game.id).thenReturn(1L)
        return RelatedGameCalculation(game)
    }
}
