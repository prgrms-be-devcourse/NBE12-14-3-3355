package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputePage
import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRequest
import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeRunResponse
import com.gamelog.nbe121423355.domain.game.dto.AdminRelatedGamePrecomputeStatusResponse
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePreviewTarget
import com.gamelog.nbe121423355.domain.game.service.AdminRelatedGamePrecomputeService
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeQueryService
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeSummary
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import com.gamelog.nbe121423355.domain.user.service.TokenReissueService
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.security.SecurityUser
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.global.security.jwt.RefreshTokenCookieFactory
import com.gamelog.nbe121423355.support.PopularControllerTestConfiguration
import jakarta.persistence.EntityManagerFactory
import java.time.Duration
import java.time.LocalDateTime
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AdminRelatedGamePrecomputeController::class)
@ContextConfiguration(classes = [AdminRelatedGamePrecomputeController::class, PopularControllerTestConfiguration::class])
@ActiveProfiles("test")
class AdminRelatedGamePrecomputeControllerTest {

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var context: ApplicationContext

    @MockitoBean
    private lateinit var service: AdminRelatedGamePrecomputeService

    @MockitoBean
    private lateinit var changeQuery: RelatedGameChangeQueryService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var tokenReissueService: TokenReissueService

    @MockitoBean
    private lateinit var refreshTokenCookieFactory: RefreshTokenCookieFactory

    @Test
    fun adminCanReadSeparateChangeCountsAndPendingTargets() {
        // given: 미확장 변경 수와 선정 대기 수가 다른 관리자 응답을 준비한다.
        `when`(changeQuery.summary()).thenReturn(RelatedGameChangeSummary(3, 7))
        `when`(changeQuery.pending(0, 10)).thenReturn(AdminRelatedGamePrecomputePage(emptyList(), 0, 10, 0, 0))

        // when: 관리자 인증으로 신규 현황·대기 목록 API를 조회한다.
        val summary = mvc.perform(get(BASE + "/changes").with(user(admin())))
        val pending = mvc.perform(get(BASE + "/changes/targets?size=10").with(user(admin())))

        // then: 두 수를 합산하지 않고 각각 반환하며 대기 목록의 페이지 계약을 유지한다.
        summary.andExpect(status().isOk()).andExpect(jsonPath("$.data.unexpandedGames").value(3))
            .andExpect(jsonPath("$.data.pendingTargets").value(7))
        pending.andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
    }

    @Test
    fun adminCanPreviewGameIdsAndNamesWithoutStartingRun() {
        // given: 관리자 미리보기의 게임 ID와 이름 응답을 준비한다.
        `when`(changeQuery.preview(1, 20)).thenReturn(AdminRelatedGamePrecomputePage(
            listOf(RelatedGamePreviewTarget("9007199254740993", "미리보기 게임")), 1, 20, 21, 2))

        // when: 관리자 인증으로 두 번째 미리보기 페이지를 조회한다.
        val result = mvc.perform(get(BASE + "/changes/preview?page=1&size=20").with(user(admin())))

        // then: 큰 ID도 문자열로 정확히 반환하고 실행 서비스를 호출하지 않는다.
        result.andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].gameId").value("9007199254740993"))
            .andExpect(jsonPath("$.data.content[0].title").value("미리보기 게임"))
        verifyNoInteractions(service)
    }

    @Test
    fun anonymousAndOrdinaryUsersCannotUseAnyEndpoint() {
        // given: 조회·실행 경로와 일반 사용자 인증 정보를 준비한다.
        val paths = listOf("/status", "/runs", "/runs/unknown", "/runs/unknown/failures", "/changes", "/changes/targets", "/changes/preview")

        // when: 인증 없이 또는 일반 사용자로 관리자 API를 호출한다.
        val anonymous = mvc.perform(post(BASE + "/runs").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ALL\"}")).andReturn()
        val ordinary = mvc.perform(post(BASE + "/runs").with(user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ALL\"}")).andReturn()

        // then: 실제 보안 체인이 익명은 401, 일반 사용자는 403으로 거절한다.
        assertThat(anonymous.response.status).isEqualTo(401)
        assertThat(ordinary.response.status).isEqualTo(403)
        for (path in paths) {
            mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized())
            mvc.perform(get(BASE + path).with(user("user").roles("USER"))).andExpect(status().isForbidden())
        }
        verifyNoInteractions(service)
    }

    @Test
    fun adminPostUsesAuthenticatedPrincipalAndReturnsAccepted() {
        // given: 로그인 관리자 ID와 접수 상태의 응답을 준비한다.
        val principal = admin()
        val run = RelatedGamePrecomputeRun(7L, RelatedGamePrecomputeTarget(
            RelatedGamePrecomputeTargetType.ALL, emptyList()), "owner", null, LocalDateTime.now())
        val response = AdminRelatedGamePrecomputeRunResponse.from(run, LocalDateTime.now(), Duration.ofMinutes(1))
        `when`(service.start(eq(7L), anyRequest())).thenReturn(response)

        // when: 본문에 임의 요청자·계산 모드를 넣어 실행을 요청한다.
        val result = mvc.perform(post(BASE + "/runs").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ALL\",\"requestedBy\":999,\"mode\":\"INITIAL_LOAD\"}")).andReturn()

        // then: 실제 principal ID를 사용하고 접수 응답·공통 응답 형식을 유지한다.
        assertThat(result.response.status).isEqualTo(202)
        val request = ArgumentCaptor.forClass(AdminRelatedGamePrecomputeRequest::class.java)
        verify(service).start(eq(7L), (request.capture() ?: AdminRelatedGamePrecomputeRequest(RelatedGamePrecomputeTargetType.ALL, null, null)))
        assertThat(request.value.type).isEqualTo(RelatedGamePrecomputeTargetType.ALL)
        assertThat(result.response.contentAsString).contains("202-1", "ACCEPTED", run.id)
    }

    @Test
    fun invalidBodyIs400BeforeServiceCall() {
        // given: 필수 종류가 없거나 잘못된 enum인 본문을 준비한다.
        val invalidBodies = listOf("{}", "{\"type\":\"UNKNOWN\"}")

        // when: 관리자로 잘못된 본문을 전송한다.
        val results = invalidBodies.map { body ->
            mvc.perform(post(BASE + "/runs").with(user(admin()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn()
        }

        // then: Bean 검증·JSON 검증을 실제 예외 응답으로 처리한다.
        for (result in results) {
            assertThat(result.response.status).isEqualTo(400)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun preservesConflictAndNotFoundResponses() {
        // given: 실행 경합과 없는 실행을 서비스가 안내하도록 준비한다.
        `when`(service.start(eq(7L), anyRequest())).thenThrow(ServiceException("409-10", "이미 실행 중입니다."))
        `when`(service.detail("missing")).thenThrow(ServiceException("404-10", "실행 이력이 없습니다."))

        // when: 실행 접수와 없는 상세 조회를 요청한다.
        val conflict = mvc.perform(post(BASE + "/runs").with(user(admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ALL\"}")).andReturn()
        val missing = mvc.perform(get(BASE + "/runs/missing").with(user(admin()))).andReturn()

        // then: ResponseAspect와 예외 처리가 각 HTTP 상태를 유지한다.
        assertThat(conflict.response.status).isEqualTo(409)
        assertThat(missing.response.status).isEqualTo(404)
    }

    @Test
    fun readEndpointsPassPaginationWithoutLoadingDatabase() {
        // given: DB가 필요 없는 웹 슬라이스에서 관리자 조회를 준비한다.
        `when`(service.status()).thenReturn(AdminRelatedGamePrecomputeStatusResponse(false, false, emptyList(), false))
        `when`(service.history(2, 5)).thenReturn(AdminRelatedGamePrecomputePage(emptyList(), 2, 5, 0, 0))
        `when`(service.failures("run", 0, 20)).thenReturn(AdminRelatedGamePrecomputePage(emptyList(), 0, 20, 0, 0))

        // when: 상태·이력·실패 목록을 조회한다.
        val statusResponse = mvc.perform(get(BASE + "/status").with(user(admin()))).andReturn()
        val history = mvc.perform(get(BASE + "/runs").with(user(admin())).param("page", "2").param("size", "5")).andReturn()
        val failures = mvc.perform(get(BASE + "/runs/run/failures").with(user(admin()))).andReturn()

        // then: 경로·기본 페이지 값을 전달하며 실제 보안·응답 구성을 DB 없이 사용한다.
        assertThat(statusResponse.response.status).isEqualTo(200)
        assertThat(history.response.status).isEqualTo(200)
        assertThat(failures.response.status).isEqualTo(200)
        verify(service).history(2, 5)
        verify(service).failures("run", 0, 20)
        assertThat(context.getBeansOfType(DataSource::class.java)).isEmpty()
        assertThat(context.getBeansOfType(EntityManagerFactory::class.java)).isEmpty()
    }

    private fun admin(): SecurityUser {
        return SecurityUser(7L, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
    }
    // Mockito 매처의 null 반환이 필수 요청 인자의 Kotlin 검사를 통과하도록 대체값을 제공한다.

    private fun anyRequest(): AdminRelatedGamePrecomputeRequest =
    any<AdminRelatedGamePrecomputeRequest>() ?: AdminRelatedGamePrecomputeRequest(RelatedGamePrecomputeTargetType.ALL, null, null)

    companion object {
        private val BASE = "/api/v1/admin/related-game-precompute"
    }
}
