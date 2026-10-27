package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse
import com.gamelog.nbe121423355.domain.game.dto.RelatedGameResponse
import com.gamelog.nbe121423355.domain.game.service.GameDetailService
import com.gamelog.nbe121423355.global.dto.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/games")
@Tag(name = "game-detail-controller", description = "게임 상세 및 연관 게임 조회 API")
@ApiResponses(value = [
    ApiResponse(responseCode = "200", description = "200-1: 조회 성공", useReturnTypeSchema = true),
    ApiResponse(responseCode = "404", description = "404-1: 공개 대상 게임 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
])
class GameDetailController(private val gameDetailService: GameDetailService) {
    // GameLog DB ID를 기준으로 게임 상세 정보 조회
    @GetMapping("/{gameId}")
    @Operation(summary = "게임 상세 조회", description = "GameLog 데이터베이스 ID로 게임 정보와 커뮤니티 통계를 조회합니다.")
    fun getGameDetail(
        @Parameter(
            description = "GameLog 게임 ID",
            example = "6",
            required = true,
        )
        @PathVariable("gameId")
        gameId: Long
    ): RsData<GameDetailResponse> {
        val response = gameDetailService.getGameDetail(gameId)
        return RsData("200-1", "게임 상세 정보를 조회했습니다.", response)
    }

    // GameLog DB ID를 기준으로 연관 추천 게임 상위 5개 조회
    @GetMapping("/{gameId}/related")
    @Operation(summary = "연관 게임 조회", description = "비로그인 조회가 가능합니다. GameLog DB 게임 ID를 기준으로 최대 5개 연관 게임을 반환합니다. 사전 계산 설정이 켜져 있고 정상 계산 결과가 있으면 저장된 후보를 사용하고, 그렇지 않으면 조회 시 계산합니다. 기준 게임은 있지만 후보가 없으면 200과 빈 목록입니다.")
    fun getRelatedGames(
        @Parameter(
            description = "GameLog 게임 ID",
            example = "6",
            required = true,
        )
        @PathVariable("gameId")
        gameId: Long
    ): RsData<List<RelatedGameResponse>> {
        val response = gameDetailService.getRelatedGames(gameId)
        return RsData("200-1", "연관 추천 게임을 조회했습니다.", response)
    }
}
