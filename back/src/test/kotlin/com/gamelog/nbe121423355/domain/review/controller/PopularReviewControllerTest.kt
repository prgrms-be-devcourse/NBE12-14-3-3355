package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.dto.response.PopularReviewResponse
import com.gamelog.nbe121423355.domain.review.service.PopularReviewService
import com.gamelog.nbe121423355.domain.user.service.TokenReissueService
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.global.security.jwt.RefreshTokenCookieFactory
import com.gamelog.nbe121423355.support.PopularControllerTestConfiguration
import jakarta.persistence.EntityManagerFactory
import java.math.BigDecimal
import java.time.LocalDateTime
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PopularReviewController::class)
@ContextConfiguration(classes = [PopularReviewController::class, PopularControllerTestConfiguration::class])
@ActiveProfiles("test")
class PopularReviewControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var popularReviewService: PopularReviewService

    @MockitoBean
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var tokenReissueService: TokenReissueService

    @MockitoBean
    private lateinit var refreshTokenCookieFactory: RefreshTokenCookieFactory

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    @DisplayName("웹 테스트는 DataSource와 JPA를 로딩하지 않는다")
    fun doesNotLoadDatabaseInfrastructure() {
        // given: 실제 보안 구성을 포함하는 인기 리뷰 웹 슬라이스를 준비한다.
        val context = applicationContext

        // when: 웹 컨텍스트에 등록된 DB와 JPA 인프라 Bean을 조회한다.
        val dataSources = context.getBeansOfType(DataSource::class.java)
        val entityManagerFactories = context.getBeansOfType(EntityManagerFactory::class.java)

        // then: DB 연결과 JPA 초기화 없이 웹 테스트를 구성한다.
        assertThat(dataSources).isEmpty()
        assertThat(entityManagerFactories).isEmpty()
    }

    @Test
    @DisplayName("비로그인 사용자가 기본 5개의 인기 리뷰를 조회한다")
    fun getPopularReviewsAllowsAnonymousUserWithDefaultSize() {
        // given: 기본 크기로 반환할 인기 리뷰 응답을 준비한다.
        val createdDate = LocalDateTime.of(2026, 9, 22, 12, 0)
        val response = PopularReviewResponse(
            reviewId = 1L,
            userId = 10L,
            nickname = "리뷰 작성자",
            profileImageUrl = "https://image.test/profile.jpg",
            gameId = 20L,
            gameTitle = "게임 제목",
            gameCoverImageUrl = "https://image.test/game.jpg",
            rating = BigDecimal("4.5"),
            content = "인기 리뷰 내용",
            spoiler = false,
            likeCount = 15L,
            createdDate = createdDate,
        )
        `when`(popularReviewService.getPopularReviews(5)).thenReturn(listOf(response))

        // when: 인증 정보와 size 없이 인기 리뷰를 조회한다.
        val result = mockMvc.perform(get("/api/v1/reviews/popular"))

        // then: 공개 조회가 성공하고 기본값 5가 Service에 전달된다.
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("인기 리뷰를 조회했습니다."))
            .andExpect(jsonPath("$.data[0].reviewId").value(1L))
            .andExpect(jsonPath("$.data[0].userId").value(10L))
            .andExpect(jsonPath("$.data[0].nickname").value("리뷰 작성자"))
            .andExpect(jsonPath("$.data[0].profileImageUrl").value("https://image.test/profile.jpg"))
            .andExpect(jsonPath("$.data[0].gameId").value(20L))
            .andExpect(jsonPath("$.data[0].gameTitle").value("게임 제목"))
            .andExpect(jsonPath("$.data[0].gameCoverImageUrl").value("https://image.test/game.jpg"))
            .andExpect(jsonPath("$.data[0].rating").value(4.5))
            .andExpect(jsonPath("$.data[0].content").value("인기 리뷰 내용"))
            .andExpect(jsonPath("$.data[0].spoiler").value(false))
            .andExpect(jsonPath("$.data[0].likeCount").value(15L))
            .andExpect(jsonPath("$.data[0].createdDate").value("2026-09-22T12:00:00"))
        verify(popularReviewService).getPopularReviews(5)
    }

    @Test
    @DisplayName("요청한 조회 개수를 인기 리뷰 Service에 전달한다")
    fun getPopularReviewsPassesRequestedSize() {
        // given: 요청한 크기의 인기 리뷰 조회 결과를 준비한다.
        `when`(popularReviewService.getPopularReviews(10)).thenReturn(emptyList())

        // when: size를 10으로 지정해 인기 리뷰를 조회한다.
        val result = mockMvc.perform(get("/api/v1/reviews/popular").param("size", "10"))

        // then: 빈 목록 응답과 함께 요청값 10이 Service에 전달된다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty())
        verify(popularReviewService).getPopularReviews(10)
    }

    @Test
    @DisplayName("허용 범위를 벗어난 조회 개수는 400 응답을 반환한다")
    fun getPopularReviewsRejectsInvalidSize() {
        // given: Service가 잘못된 조회 개수 예외를 반환하도록 준비한다.
        `when`(popularReviewService.getPopularReviews(51))
            .thenThrow(ServiceException("400-10", "조회 개수는 1 이상 50 이하여야 합니다."))

        // when: 최대값을 초과한 size로 인기 리뷰를 조회한다.
        val result = mockMvc.perform(get("/api/v1/reviews/popular").param("size", "51"))

        // then: 검증 오류 메시지를 포함한 400 응답을 반환한다.
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.resultCode").value("400-10"))
            .andExpect(jsonPath("$.msg").value("조회 개수는 1 이상 50 이하여야 합니다."))
    }
}
