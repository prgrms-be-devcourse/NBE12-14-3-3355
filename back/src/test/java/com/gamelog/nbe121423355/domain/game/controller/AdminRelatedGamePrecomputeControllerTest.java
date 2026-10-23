package com.gamelog.nbe121423355.domain.game.controller;

import com.gamelog.nbe121423355.domain.game.dto.*;
import com.gamelog.nbe121423355.domain.game.entity.RelatedGamePrecomputeRun;
import com.gamelog.nbe121423355.domain.game.service.AdminRelatedGamePrecomputeService;
import com.gamelog.nbe121423355.domain.game.service.model.*;
import com.gamelog.nbe121423355.domain.user.service.TokenReissueService;
import com.gamelog.nbe121423355.global.exception.ServiceException;
import com.gamelog.nbe121423355.global.security.SecurityUser;
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider;
import com.gamelog.nbe121423355.global.security.jwt.RefreshTokenCookieFactory;
import com.gamelog.nbe121423355.support.PopularControllerTestConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminRelatedGamePrecomputeController.class)
@ContextConfiguration(classes = {AdminRelatedGamePrecomputeController.class, PopularControllerTestConfiguration.class})
@ActiveProfiles("test")
class AdminRelatedGamePrecomputeControllerTest {
    private static final String BASE = "/api/v1/admin/related-game-precompute";
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @MockitoBean AdminRelatedGamePrecomputeService service;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean TokenReissueService tokenReissueService;
    @MockitoBean RefreshTokenCookieFactory refreshTokenCookieFactory;

    @Test
    void anonymousAndOrdinaryUsersCannotUseAnyEndpoint() throws Exception {
        // given: 조회·실행 경로와 일반 사용자 인증 정보를 준비한다.
        var paths = List.of("/status", "/runs", "/runs/unknown", "/runs/unknown/failures");

        // when: 인증 없이 또는 일반 사용자로 관리자 API를 호출한다.
        var anonymous = mvc.perform(post(BASE + "/runs").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ALL\"}")).andReturn();
        var ordinary = mvc.perform(post(BASE + "/runs").with(user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ALL\"}")).andReturn();

        // then: 실제 보안 체인이 익명은 401, 일반 사용자는 403으로 거절한다.
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(ordinary.getResponse().getStatus()).isEqualTo(403);
        for (String path : paths) {
            mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
            mvc.perform(get(BASE + path).with(user("user").roles("USER"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminPostUsesAuthenticatedPrincipalAndReturnsAccepted() throws Exception {
        // given: 로그인 관리자 ID와 접수 상태의 응답을 준비한다.
        var principal = admin();
        var run = new RelatedGamePrecomputeRun(7L, new RelatedGamePrecomputeTarget(
                RelatedGamePrecomputeTargetType.ALL, List.of()), "owner", null, LocalDateTime.now());
        var response = AdminRelatedGamePrecomputeRunResponse.Companion.from(run, LocalDateTime.now(), Duration.ofMinutes(1));
        when(service.start(eq(7L), any())).thenReturn(response);

        // when: 본문에 임의 요청자·계산 모드를 넣어 실행을 요청한다.
        var result = mvc.perform(post(BASE + "/runs").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"ALL\",\"requestedBy\":999,\"mode\":\"INITIAL_LOAD\"}")).andReturn();

        // then: 실제 principal ID를 사용하고 접수 응답·공통 응답 형식을 유지한다.
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        var request = ArgumentCaptor.forClass(AdminRelatedGamePrecomputeRequest.class);
        verify(service).start(eq(7L), request.capture());
        assertThat(request.getValue().getType()).isEqualTo(RelatedGamePrecomputeTargetType.ALL);
        assertThat(result.getResponse().getContentAsString()).contains("202-1", "ACCEPTED", run.getId());
    }

    @Test
    void invalidBodyIs400BeforeServiceCall() throws Exception {
        // given: 필수 종류가 없거나 잘못된 enum인 본문을 준비한다.
        var invalidBodies = List.of("{}", "{\"type\":\"UNKNOWN\"}");

        // when: 관리자로 잘못된 본문을 전송한다.
        for (String body : invalidBodies) {
            var result = mvc.perform(post(BASE + "/runs").with(user(admin()))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

            // then: Bean 검증·JSON 검증을 실제 예외 응답으로 처리한다.
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
        }
        verifyNoInteractions(service);
    }

    @Test
    void preservesConflictAndNotFoundResponses() throws Exception {
        // given: 실행 경합과 없는 실행을 서비스가 안내하도록 준비한다.
        when(service.start(eq(7L), any())).thenThrow(new ServiceException("409-10", "이미 실행 중입니다."));
        when(service.detail("missing")).thenThrow(new ServiceException("404-10", "실행 이력이 없습니다."));

        // when: 실행 접수와 없는 상세 조회를 요청한다.
        var conflict = mvc.perform(post(BASE + "/runs").with(user(admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"ALL\"}")).andReturn();
        var missing = mvc.perform(get(BASE + "/runs/missing").with(user(admin()))).andReturn();

        // then: ResponseAspect와 예외 처리가 각 HTTP 상태를 유지한다.
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void readEndpointsPassPaginationWithoutLoadingDatabase() throws Exception {
        // given: DB가 필요 없는 웹 슬라이스에서 관리자 조회를 준비한다.
        when(service.status()).thenReturn(new AdminRelatedGamePrecomputeStatusResponse(false, false, List.of(), false));
        when(service.history(2, 5)).thenReturn(new AdminRelatedGamePrecomputePage<>(List.of(), 2, 5, 0, 0));
        when(service.failures("run", 0, 20)).thenReturn(new AdminRelatedGamePrecomputePage<>(List.of(), 0, 20, 0, 0));

        // when: 상태·이력·실패 목록을 조회한다.
        var statusResponse = mvc.perform(get(BASE + "/status").with(user(admin()))).andReturn();
        var history = mvc.perform(get(BASE + "/runs").with(user(admin())).param("page", "2").param("size", "5")).andReturn();
        var failures = mvc.perform(get(BASE + "/runs/run/failures").with(user(admin()))).andReturn();

        // then: 경로·기본 페이지 값을 전달하며 실제 보안·응답 구성을 DB 없이 사용한다.
        assertThat(statusResponse.getResponse().getStatus()).isEqualTo(200);
        assertThat(history.getResponse().getStatus()).isEqualTo(200);
        assertThat(failures.getResponse().getStatus()).isEqualTo(200);
        verify(service).history(2, 5);
        verify(service).failures("run", 0, 20);
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
    }

    private SecurityUser admin() {
        return new SecurityUser(7L, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
