package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.dto.PopularGameResponse
import com.gamelog.nbe121423355.domain.game.service.PopularGameService
import com.gamelog.nbe121423355.domain.user.service.TokenReissueService
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.global.security.jwt.RefreshTokenCookieFactory
import com.gamelog.nbe121423355.support.PopularControllerTestConfiguration
import jakarta.persistence.EntityManagerFactory
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

@WebMvcTest(PopularGameController::class)
@ContextConfiguration(classes = [PopularGameController::class, PopularControllerTestConfiguration::class])
@ActiveProfiles("test")
class PopularGameControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var popularGameService: PopularGameService

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
        // given: 실제 보안 구성을 포함하는 인기 게임 웹 슬라이스를 준비한다.
        val context = applicationContext

        // when: 웹 컨텍스트에 등록된 DB와 JPA 인프라 Bean을 조회한다.
        val dataSources = context.getBeansOfType(DataSource::class.java)
        val entityManagerFactories = context.getBeansOfType(EntityManagerFactory::class.java)

        // then: DB 연결과 JPA 초기화 없이 웹 테스트를 구성한다.
        assertThat(dataSources).isEmpty()
        assertThat(entityManagerFactories).isEmpty()
    }

    @Test
    @DisplayName("비로그인 사용자가 기본 5개의 인기 게임을 조회한다")
    fun getPopularGamesAllowsAnonymousUserWithDefaultSize() {
        // given: 기본 크기로 반환할 인기 게임 응답을 준비한다.
        val response = PopularGameResponse(
            gameId = 1L,
            title = "인기 게임",
            coverImageUrl = "https://image.test/game.jpg",
            likeCount = 10L,
            genres = listOf(PopularGameResponse.GenreResponse(id = 11L, name = "Action")),
        )
        `when`(popularGameService.getPopularGames(5)).thenReturn(listOf(response))

        // when: 인증 정보와 size 없이 인기 게임을 조회한다.
        val result = mockMvc.perform(get("/api/v1/games/popular"))

        // then: 공개 조회가 성공하고 기본값 5가 Service에 전달된다.
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.msg").value("인기 게임을 조회했습니다."))
            .andExpect(jsonPath("$.data[0].gameId").value(1L))
            .andExpect(jsonPath("$.data[0].title").value("인기 게임"))
            .andExpect(jsonPath("$.data[0].coverImageUrl").value("https://image.test/game.jpg"))
            .andExpect(jsonPath("$.data[0].likeCount").value(10L))
            .andExpect(jsonPath("$.data[0].genres[0].id").value(11L))
            .andExpect(jsonPath("$.data[0].genres[0].name").value("Action"))
        verify(popularGameService).getPopularGames(5)
    }

    @Test
    @DisplayName("요청한 조회 개수를 인기 게임 Service에 전달한다")
    fun getPopularGamesPassesRequestedSize() {
        // given: 요청한 크기의 인기 게임 조회 결과를 준비한다.
        `when`(popularGameService.getPopularGames(10)).thenReturn(emptyList())

        // when: size를 10으로 지정해 인기 게임을 조회한다.
        val result = mockMvc.perform(get("/api/v1/games/popular").param("size", "10"))

        // then: 빈 목록 응답과 함께 요청값 10이 Service에 전달된다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty())
        verify(popularGameService).getPopularGames(10)
    }

    @Test
    @DisplayName("허용 범위를 벗어난 조회 개수는 400 응답을 반환한다")
    fun getPopularGamesRejectsInvalidSize() {
        // given: Service가 잘못된 조회 개수 예외를 반환하도록 준비한다.
        `when`(popularGameService.getPopularGames(51))
            .thenThrow(ServiceException("400-9", "조회 개수는 1 이상 50 이하여야 합니다."))

        // when: 최대값을 초과한 size로 인기 게임을 조회한다.
        val result = mockMvc.perform(get("/api/v1/games/popular").param("size", "51"))

        // then: 검증 오류 메시지를 포함한 400 응답을 반환한다.
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.resultCode").value("400-9"))
            .andExpect(jsonPath("$.msg").value("조회 개수는 1 이상 50 이하여야 합니다."))
    }
}
