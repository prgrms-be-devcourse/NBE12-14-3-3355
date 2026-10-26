package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRequest
import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRunResponse
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeTargetRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeAdminProperties
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AdminRelatedGamePrecomputeServiceTest {

    @Mock
    private lateinit var targets: RelatedGamePrecomputeTargetRepository

    @Mock
    private lateinit var store: RelatedGamePrecomputeRunStore

    @Mock
    private lateinit var query: RelatedGamePrecomputeRunQueryService

    @Mock
    private lateinit var batch: RelatedGamePrecomputeBatchService

    @Mock
    private lateinit var tracker: RelatedGamePrecomputeRunTracker

    @Mock
    private lateinit var lock: RelatedGamePrecomputeLock

    @Mock
    private lateinit var recovery: RelatedGamePrecomputeRecoveryService
    private lateinit var service: AdminRelatedGamePrecomputeService

    @BeforeEach
    fun setUp() {
        service = AdminRelatedGamePrecomputeService(targets, store, query, batch, tracker, lock, recovery,
            RelatedGamePrecomputeAdminProperties())
    }

    @AfterEach
    fun stopWorker() {
        service.stop()
    }

    @Test
    fun baselineOptionIsRejectedForPartialRunsBeforeCreatingHistory() {
        // given: 변경 기반 부분 갱신에 초기 기준선 옵션을 잘못 지정한다.
        val request = AdminRelatedGamePrecomputeRequest(RelatedGamePrecomputeTargetType.CHANGED, null, null, true)

        // when: 초기 기준선 접수를 시도한다.
        val error = catchThrowable { service.start(7, request) }

        // then: 전체 갱신 전용 옵션임을 안내하고 이력·계산을 만들지 않는다.
        assertThat(error).isInstanceOf(ServiceException::class.java).hasMessageContaining("전체 갱신")
        verifyNoInteractions(store, batch)
    }

    @Test
    fun invalidIdRejectsWholeRequestBeforeDatabaseWrite() {
        // given: 양수 ID 사이에 잘못된 문자열을 포함한다.
        val request = request(RelatedGamePrecomputeTargetType.IDS, "1, invalid 2", null)

        // when: 지정 ID 실행을 접수한다.
        val error = catchThrowable { service.start(7, request) }

        // then: 오류 토큰을 안내하고 이력 생성·계산은 수행하지 않는다.
        assertThat(error).isInstanceOf(ServiceException::class.java).hasMessageContaining("invalid")
        verifyNoInteractions(targets, store, batch)
    }

    @Test
    fun missingIdRejectsWholeRequest() {
        // given: 요청한 두 게임 중 하나만 DB에 존재한다.
        `when`(targets.findExistingIds(listOf(1L, 2L))).thenReturn(listOf(1L))

        // when: 두 게임의 계산을 요청한다.
        val error = catchThrowable { service.start(7, request(RelatedGamePrecomputeTargetType.IDS, "1,2", null)) }

        // then: 누락 ID를 안내하며 존재하는 게임도 부분 실행하지 않는다.
        assertThat(error).isInstanceOf(ServiceException::class.java).hasMessageContaining("2")
        verifyNoInteractions(store, batch)
    }

    @Test
    fun rejectsMoreThanOneThousandNormalizedIdsBeforeQuery() {
        // given: 중복 제거 이후에도 1,001개인 ID 입력을 준비한다.
        val ids = (1L..1_001L).joinToString(",")

        // when: 지정 ID 계산을 접수한다.
        val error = catchThrowable { service.start(7, request(RelatedGamePrecomputeTargetType.IDS, ids, null)) }

        // then: 개수 제한을 확인하고 불필요한 DB 조회도 수행하지 않는다.
        assertThat(error).isInstanceOf(ServiceException::class.java).hasMessageContaining("1,000")
        verifyNoInteractions(targets, store, batch)
    }

    @Test
    fun normalizesSeparatorsAndAlwaysUsesRefresh() {
        // given: 중복·쉼표·공백·줄바꿈을 포함한 입력과 실행 기반을 준비한다.
        `when`(targets.findExistingIds(listOf(1L, 2L, 3L))).thenReturn(listOf(1L, 2L, 3L))
        val accepted = mock(AdminRelatedGamePrecomputeRunResponse::class.java)
        `when`(store.create(eq(7L), anyTarget(), anyString(), isNull())).thenReturn("run-1")
        `when`(query.detail("run-1")).thenReturn(accepted)
        `when`(tracker.observer("run-1")).thenReturn(RelatedGamePrecomputeObserver.NONE)
        val finished = CountDownLatch(1)
        `when`(batch.run(anyTarget(), refreshMode(), anyObserver()))
            .thenAnswer {
                finished.countDown()
                RelatedGamePrecomputeBatchService.Result(true, 3, 3, emptyList(), Duration.ZERO)
            }

        // when: 관리자 요청을 비동기로 접수한다.
        val response = service.start(7, request(RelatedGamePrecomputeTargetType.IDS, "3, 1\n2 1", null))
        val executed = finished.await(3, TimeUnit.SECONDS)

        // then: 접수 응답을 반환하고 정규화한 ID와 REFRESH만 배치로 전달한다.
        assertThat(response).isSameAs(accepted)
        assertThat(executed).isTrue()
        val target = ArgumentCaptor.forClass(RelatedGamePrecomputeTarget::class.java)
        verify(batch).run((target.capture() ?: RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL)), refreshMode(), anyObserver())
        assertThat(target.value.gameIds).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun externalLockRejectsAdmissionWithoutCreatingHistory() {
        // given: CLI 또는 다른 서버가 공통 잠금을 보유한 상태다.
        `when`(lock.isLocked()).thenReturn(true)

        // when: 전체 갱신을 접수한다.
        val error = catchThrowable { service.start(7, request(RelatedGamePrecomputeTargetType.ALL, null, null)) }

        // then: 409로 거절하고 새 이력·계산을 시작하지 않는다.
        assertThat(error).isInstanceOfSatisfying(ServiceException::class.java,
            { e -> assertThat(e.resultCode).isEqualTo("409-10") })
        verifyNoInteractions(store, batch)
    }

    @Test
    fun retryCannotChangeOriginalExecutionTypeOrIds() {
        // given: 원본은 게임 1 실행이며 새 요청의 게임 2도 DB에는 존재한다.
        `when`(targets.findExistingIds(listOf(2L))).thenReturn(listOf(2L))
        `when`(query.replayTarget("source")).thenReturn(RelatedGamePrecomputeTarget(
            RelatedGamePrecomputeTargetType.IDS, listOf(1L)))

        // when: 원본 실행을 연결하면서 다른 게임을 지정한다.
        val error = catchThrowable { service.start(7, request(RelatedGamePrecomputeTargetType.IDS, "2", "source")) }

        // then: 원본과 다른 재실행을 거절하고 새 이력을 만들지 않는다.
        assertThat(error).isInstanceOf(ServiceException::class.java).hasMessageContaining("원본 실행")
        verifyNoInteractions(store, batch)
    }

    @Test
    fun freshRemoteAdmissionPreventsExecutionBeforeLockAcquisition() {
        // given: 다른 서버가 아직 잠금 획득 전이지만 정상 접수 이력을 유지하고 있다.
        `when`(query.hasRecentActiveRun()).thenReturn(true)

        // when: 같은 DB에 추가 관리자 실행을 요청한다.
        val error = catchThrowable { service.start(7, request(RelatedGamePrecomputeTargetType.ALL, null, null)) }

        // then: 현재 잠금이 비어 있어도 살아 있는 접수를 존중하고 추가 이력을 만들지 않는다.
        assertThat(error).isInstanceOfSatisfying(ServiceException::class.java,
            { e -> assertThat(e.resultCode).isEqualTo("409-10") })
        verifyNoInteractions(store, batch)
    }

    @Test
    fun executorShutdownAfterAdmissionRecordsRejectedRun() {
        // given: 이력 생성 직후 서버 종료로 worker 제출이 거절되는 상황을 준비한다.
        val accepted = mock(AdminRelatedGamePrecomputeRunResponse::class.java)
        `when`(store.create(eq(7L), anyTarget(), anyString(), isNull())).thenReturn("run-1")
        `when`(query.detail("run-1")).thenAnswer {
            service.stop()
            accepted
        }

        // when: 이력을 생성했지만 worker에는 전달할 수 없는 실행을 접수한다.
        val error = catchThrowable { service.start(7, request(RelatedGamePrecomputeTargetType.ALL, null, null)) }

        // then: ACCEPTED 이력을 방치하지 않고 실행 거절로 정리하며 409를 반환한다.
        assertThat(error).isInstanceOfSatisfying(ServiceException::class.java,
            { e -> assertThat(e.resultCode).isEqualTo("409-10") })
        verify(store).reject("run-1")
        verifyNoInteractions(batch)
    }

    private fun request(type: RelatedGamePrecomputeTargetType, ids: String?, source: String?): AdminRelatedGamePrecomputeRequest =
    AdminRelatedGamePrecomputeRequest(type = type, gameIds = ids, sourceRunId = source)
    // Mockito는 매처 등록 시 null을 반환하므로 Kotlin 필수 인자에 대체값을 제공한다.

    private fun anyTarget(): RelatedGamePrecomputeTarget =
    any<RelatedGamePrecomputeTarget>() ?: RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.ALL)

    private fun anyObserver(): RelatedGamePrecomputeObserver =
    any<RelatedGamePrecomputeObserver>() ?: RelatedGamePrecomputeObserver.NONE

    private fun refreshMode(): RelatedGamePrecomputeMode =
    eq(RelatedGamePrecomputeMode.REFRESH) ?: RelatedGamePrecomputeMode.REFRESH
}
