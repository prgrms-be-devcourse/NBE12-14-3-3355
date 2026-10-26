package com.gamelog.nbe121423355.domain.usergame.controller

import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
import com.gamelog.nbe121423355.domain.usergame.service.UserGameProfileService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "user-game-profile-controller", description = "사용자 게임 프로필·공개 게임 목록·인생 게임 API")
class UserGameProfileController(
    private val userGameService: UserGameService,
    private val userGameProfileService: UserGameProfileService,
) {
    @GetMapping("/{userId}/games")
    @Operation(summary = "사용자 프로필 게임 목록 조회", description = "비로그인 조회가 가능합니다. 특정 사용자의 공개 게임 기록을 page(0부터)·size(기본 60, 1~100)와 상태·검색·필터·정렬 조건으로 조회합니다.")
    fun getUserProfileGames(
        @PathVariable userId: Long,
        @ParameterObject @Valid @ModelAttribute request: UserGameSearchRequest,
    ): RsData<UserGameLibraryResponse> {
        val pages =
            userGameService.getPublicUserGameList(
                userId = userId,
                request = request,
            )
        return RsData(
            resultCode = "200-1",
            msg = "라이브러리 게임 목록을 조회했습니다.",
            data =
                UserGameLibraryResponse(
                    totalElements = pages.totalElements,
                    totalPages = pages.totalPages,
                    userGames = pages.content,
                ),
        )
    }

    @GetMapping("/me/game-profile")
    @Operation(summary = "내 프로필 게임 통계 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun profileTab(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser
    ): RsData<UserProfileResponse> {
        val response =
            userGameProfileService.profileTab(
                targetUserId = user.id,
                currentUserId = user.id,
            )

        return RsData(
            resultCode = "200-1",
            msg = "프로필 정보를 조회했습니다.",
            data = response,
        )
    }

    @GetMapping("/{userId}/game-profile")
    @Operation(summary = "사용자 프로필 게임 통계 조회", description = "특정 사용자의 공개 프로필 기록과 게임 통계를 조회합니다.")
    fun getProfile(
        @PathVariable userId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser?,
    ): RsData<UserProfileResponse> {
        val currentUserId = user?.id
        val response =
            userGameProfileService.profileTab(
                targetUserId = userId,
                currentUserId = currentUserId,
            )

        return RsData(
            resultCode = "200-1",
            msg = "프로필을 조회했습니다.",
            data = response,
        )
    }

    @PutMapping("/me/favorite-games")
    @Operation(
        summary = "인생 게임 수정",
        description = "본인 프로필의 인생 게임 목록을 전체 교체합니다. gameIds는 라이브러리에 포함된 게임만 최대 5개까지 중복 없이 전달하며 배열 순서대로 표시합니다. 빈 배열은 전체 해제입니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 인생 게임 목록 수정 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-11: 라이브러리 밖의 게임 포함 / 400-2: JSON 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "500", description = "500-1: 현재 개수 초과·중복 ID·사용자 없음은 IllegalArgumentException으로 처리되어 서버 오류를 반환합니다.", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun updateFavoriteGames(
        @RequestBody request: UserFavoriteGameRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<List<UserFavoriteGameResponse>> {
        val response: List<UserFavoriteGameResponse> =
            userGameProfileService.updateFavoriteGames(
                userId = user.id,
                gameIds = request.gameIds,
            )
        return RsData(
            resultCode = "200-1",
            msg = "인생게임을 수정했습니다.",
            data = response,
        )
    }
}
