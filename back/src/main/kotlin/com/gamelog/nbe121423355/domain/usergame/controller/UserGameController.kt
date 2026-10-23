package com.gamelog.nbe121423355.domain.usergame.controller

import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
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
@RequestMapping("/api/v1/library/games")
@Tag(name = "user-game-controller", description = "내 게임 라이브러리·플레이 상태·프로필 기록 API")
class UserGameController constructor(private val userGameService: UserGameService) {

    @GetMapping("")
    @Operation(
        summary = "내 게임 목록 조회",
        description = "상태, 플랫폼, 장르, 검색어와 정렬 조건으로 내 게임 목록을 조회합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun getUserGameList(
        @Valid @ModelAttribute request: @Valid UserGameSearchRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameLibraryResponse> {
        val pages = userGameService.getUserGameList(user.id, request)

        return RsData(
            "200-1",
            "라이브러리 게임 목록을 조회했습니다.",
            UserGameLibraryResponse(pages.totalElements, pages.totalPages, pages.content),
        )
    }

    @GetMapping("/profile/{userId}/games")
    @Operation(summary = "사용자 프로필 게임 목록 조회", description = "특정 사용자의 공개 게임 기록을 페이지 단위로 조회합니다.")
    fun getUserProfileGames(
        @PathVariable userId: Long,
        @Valid @ModelAttribute request: @Valid UserGameSearchRequest,
    ): RsData<UserGameLibraryResponse> {
        val pages = userGameService.getPublicUserGameList(userId, request)
        return RsData(
            "200-1",
            "라이브러리 게임 목록을 조회했습니다.",
            UserGameLibraryResponse(pages.totalElements, pages.totalPages, pages.content),
        )
    }

    // 등록 및 수정
    @PostMapping("/{gameId}")
    @Operation(summary = "게임 기록 등록 또는 수정", security = [SecurityRequirement(name = "bearerAuth")])
    fun addGameToLibrary(
        @PathVariable gameId: Long,
        @Valid @RequestBody reqBody: @Valid UserGameReqBody,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameDto> {

        val result = userGameService.addOrUpdateGameToLibrary(user.id, gameId, reqBody)
        val message = if (result.created) "라이브러리에 게임을 등록했습니다." else "게임 기록을 수정했습니다."
        val code = if (result.created) "201-1" else "200-1"

        return RsData(code, message, UserGameDto(result.userGame))
    }

    // 수정만
    @PatchMapping("/{gameId}")
    @Operation(summary = "게임 기록 수정", security = [SecurityRequirement(name = "bearerAuth")])
    fun updatePlayRecord(
        @PathVariable gameId: Long,
        @Valid @RequestBody reqBody: @Valid UserGameReqBody,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameDto> {

        val userGame = userGameService.updatePlayRecord(user.id, gameId, reqBody)
        return RsData("200-1", "게임 기록을 수정했습니다.", UserGameDto(userGame))
    }

    @PutMapping("/{gameId}/play-status")
    @Operation(
        summary = "플레이 완료 상태 변경",
        description = "PLAYED, COMPLETED, RETIRED, SHELVED, DROPPED 중 하나를 저장하며, 값을 생략하면 상태를 해제합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun changePlayStatus(
        @PathVariable gameId: Long,
        @RequestParam(required = false) status: PlayStatus?,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGamePlayStatusResponse> {
        val playStatus = userGameService.changePlayed(user.id, gameId, status)

        return RsData(
            "200-1",
            "플레이 상태를 변경했습니다.",
            UserGamePlayStatusResponse(playStatus),
        )
    }

    @PatchMapping("/{gameId}/playing")
    @Operation(summary = "플레이 중 상태 변경", security = [SecurityRequirement(name = "bearerAuth")])
    fun changePlaying(
        @PathVariable gameId: Long,
        @RequestParam playing: Boolean,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameStatusResponse> {
        val playingStatus = userGameService.changePlaying(user.id, gameId, playing)

        return RsData(
            "200-1",
            "플레이 중 상태를 변경했습니다.",
            UserGameStatusResponse(playingStatus),
        )
    }

    @PatchMapping("/{gameId}/wishlist")
    @Operation(summary = "위시리스트 상태 변경", security = [SecurityRequirement(name = "bearerAuth")])
    fun changeWishlist(
        @PathVariable gameId: Long,
        @RequestParam wishlist: Boolean,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameStatusResponse> {
        val wishlistStatus = userGameService.changeWishlist(user.id, gameId, wishlist)

        return RsData(
            "200-1",
            "위시리스트 상태를 변경했습니다.",
            UserGameStatusResponse(wishlistStatus),
        )
    }

    @PatchMapping("/{gameId}/backlog")
    @Operation(summary = "플레이 예정 상태 변경", security = [SecurityRequirement(name = "bearerAuth")])
    fun changeBacklog(
        @PathVariable gameId: Long,
        @RequestParam backlog: Boolean,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameStatusResponse> {
        val backlogStatus = userGameService.changeBacklog(user.id, gameId, backlog)

        return RsData(
            "200-1",
            "백로그 상태를 변경했습니다.",
            UserGameStatusResponse(backlogStatus),
        )
    }

    @PatchMapping("/{gameId}/liked")
    @Operation(
        summary = "좋아하는 게임 상태 변경",
        description = "프로필 Likes 탭에 표시할 게임의 하트 상태를 변경합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun changeLiked(
        @PathVariable gameId: Long,
        @RequestParam liked: Boolean,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameStatusResponse> {
        val likedStatus = userGameService.changeLiked(user.id, gameId, liked)

        return RsData(
            "200-1",
            "좋아요 상태를 변경했습니다.",
            UserGameStatusResponse(likedStatus),
        )
    }

    @GetMapping("/profile")
    @Operation(summary = "내 프로필 게임 통계 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun profileTab(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser
    ): RsData<UserProfileResponse> {
        val response = userGameService.profileTab(user.id, user.id)

        return RsData("200-1", "프로필 정보를 조회했습니다.", response)
    }

    @GetMapping("/profile/{userId}")
    @Operation(summary = "사용자 프로필 게임 통계 조회", description = "특정 사용자의 공개 프로필 기록과 게임 통계를 조회합니다.")
    fun getProfile(
        @PathVariable userId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser?,
    ): RsData<UserProfileResponse> {
        val currentUserId = user?.id
        val response = userGameService.profileTab(userId, currentUserId)

        return RsData("200-1", "프로필을 조회했습니다.", response)
    }

    @GetMapping("/recentGames")
    @Operation(summary = "최근 플레이 게임 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun recentGameList(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser
    ): RsData<List<UserGameListResponse>> {
        return RsData(
            "200-1",
            "최근 플레이한 게임을 불러왔습니다",
            userGameService.getRecentPlayedGames(user.id),
        )
    }

    @PutMapping("/favorite-games")
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
            userGameService.updateFavoriteGames(user.id, requireNotNull(request.gameIds))
        return RsData("200-1", "인생게임을 수정했습니다.", response)
    }
}
