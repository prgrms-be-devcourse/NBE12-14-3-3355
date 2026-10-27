package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.dto.GameListResponse
import com.gamelog.nbe121423355.domain.game.dto.GameSearchRequest
import com.gamelog.nbe121423355.domain.game.service.GameService
import com.gamelog.nbe121423355.global.dto.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/games")
@Tag(name = "game-controller", description = "게임 검색 및 목록 조회 API")
class GameController constructor(private val gameService: GameService) {
    @GetMapping("/suggestions")
    @Operation(summary = "게임 검색어 자동완성", description = "비로그인 조회가 가능합니다. 제목 검색 후보를 반환하며 keyword 생략·공백은 200과 빈 목록입니다.")
    fun suggestions(
        @Parameter(description = "게임 제목 검색어", example = "Baldur")
        @RequestParam(
            name = "keyword",
            required = false,
        )
        keyword: String?
    ): RsData<List<GameListResponse>> {
        return RsData<List<GameListResponse>>(
            "200-1",
            "게임 검색 후보를 조회했습니다.",
            gameService.getSuggestions(keyword),
        )
    }

    // 페이징 목록 조회
    @GetMapping("/page")
    @Operation(summary = "게임 목록 조회", description = "비로그인 조회가 가능합니다. page는 0부터, size는 기본 20(1~100)입니다. sort 미지정 시 IGDB 평점 기준이며 허용 enum은 검색 조건 스키마를 확인하세요. 장르·플랫폼은 GameLog DB ID 목록으로 genreIds=12,31&platformIds=6,48처럼 전달합니다. 결과가 없으면 200과 빈 content입니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 게임 페이지 조회 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "페이지 범위·정렬 enum·ID 등 검색 조건 검증 또는 형식 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun page(
        @ParameterObject @Valid @ModelAttribute request: @Valid GameSearchRequest
    ): RsData<Page<GameListResponse>> {
        val responsePage = gameService.getGamesPage(request)
        return RsData<Page<GameListResponse>>("200-1", "게임 목록을 페이지 단위로 조회했습니다.", responsePage)
    }
}
