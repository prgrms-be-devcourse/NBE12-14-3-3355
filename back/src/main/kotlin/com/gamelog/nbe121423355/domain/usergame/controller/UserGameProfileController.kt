package com.gamelog.nbe121423355.domain.usergame.controller

import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "user-game-profile-controller", description = "사용자 게임 프로필·공개 게임 목록·인생 게임 API")
class UserGameProfileController(
    private val userGameService: UserGameService,
) {
    @GetMapping("/{userId}/games")
    @Operation(summary = "사용자 프로필 게임 목록 조회", description = "특정 사용자의 공개 게임 기록을 페이지 단위로 조회합니다.")
    fun getUserProfileGames(
        @PathVariable userId: Long,
        @Valid @ModelAttribute request: UserGameSearchRequest,
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
            userGameService.profileTab(
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
            userGameService.profileTab(
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
        description = "프로필에 노출할 인생 게임 목록과 순서를 저장합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun updateFavoriteGames(
        @RequestBody request: UserFavoriteGameRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<List<UserFavoriteGameResponse>> {
        val response: List<UserFavoriteGameResponse> =
            userGameService.updateFavoriteGames(
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
