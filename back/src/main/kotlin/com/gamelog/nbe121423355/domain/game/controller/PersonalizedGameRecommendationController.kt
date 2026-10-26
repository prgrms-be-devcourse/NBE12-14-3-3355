package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.dto.PersonalizedGameRecommendationResponse
import com.gamelog.nbe121423355.domain.game.service.PersonalizedGameRecommendationService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 로그인 사용자의 게임 기록 또는 온보딩 선택을 기준으로 맞춤 게임을 추천
@RestController
@RequestMapping("/api/v1/games/recommendations")
@Tag(name = "personalized-game-recommendation-controller", description = "로그인 사용자 맞춤 게임 추천 API")
class PersonalizedGameRecommendationController(private val recommendationService: PersonalizedGameRecommendationService) {
    // 인증 토큰의 사용자 ID를 기준으로 맞춤 추천 게임을 최대 5개 반환
    @GetMapping("/personalized")
    @Operation(
        summary = "맞춤 게임 추천",
        description = "게임 기록 또는 온보딩 선호 정보를 기준으로 게임을 최대 5개 추천합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun getPersonalizedRecommendations(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<List<PersonalizedGameRecommendationResponse>> {
        val response = recommendationService.recommend(securityUser.id)
        return RsData(
            "200-1",
            "사용자 맞춤 추천 게임을 조회했습니다.",
            response,
        )
    }
}
