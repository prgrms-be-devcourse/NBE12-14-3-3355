package com.gamelog.nbe121423355.domain.usergame.controller

import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
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
@RequestMapping("/api/v1/library/games")
@Tag(name = "user-game-controller", description = "내 게임 라이브러리·플레이 상태 API")
@ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))])
class UserGameController(
    private val userGameService: UserGameService,
) {

    @GetMapping("")
    @Operation(
        summary = "내 게임 목록 조회",
        description = "상태·플랫폼·장르·제목·정렬 조건으로 본인의 게임 목록을 조회합니다. page는 0부터, size 기본값은 60(1~100)입니다. ID 목록은 genreIds=5,12 또는 동일 이름의 반복 파라미터로 전달할 수 있습니다. 일치하는 기록이 없으면 200과 빈 목록입니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponse(responseCode = "200", description = "200-1: 내 게임 목록", useReturnTypeSchema = true)
    fun getUserGameList(
        @ParameterObject @Valid @ModelAttribute request: UserGameSearchRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameLibraryResponse> {
        val pages =
            userGameService.getUserGameList(
                userId = user.id,
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

    // 등록 및 수정
    @PostMapping("/{gameId}")
    @Operation(summary = "게임 기록 등록 또는 수정", description = "게임 ID로 내 기록을 등록하거나 기존 기록을 수정합니다. 신규 생성은 HTTP 201·201-1, 기존 기록 수정은 HTTP 200·200-1입니다. 부분 상태 변경에는 전용 상태 API를 사용합니다. 이 요청은 리뷰 본문을 수정하지 않습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-1: 내 게임 기록 신규 생성", useReturnTypeSchema = true),
        ApiResponse(responseCode = "200", description = "200-1: 기존 내 기록 수정", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "요청 시간·날짜·플랫폼 등 검증 실패. resultCode와 msg를 확인하세요.", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "대상 게임 또는 사용자 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun addGameToLibrary(
        @PathVariable gameId: Long,
        @Valid @RequestBody reqBody: UserGameReqBody,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameDto> {

        val result =
            userGameService.addOrUpdateGameToLibrary(
                userId = user.id,
                gameId = gameId,
                reqBody = reqBody,
            )
        val message = if (result.created) "라이브러리에 게임을 등록했습니다." else "게임 기록을 수정했습니다."
        val code = if (result.created) "201-1" else "200-1"

        return RsData(
            resultCode = code,
            msg = message,
            data = UserGameDto(userGame = result.userGame),
        )
    }

    // 수정만
    @PatchMapping("/{gameId}")
    @Operation(summary = "게임 기록 수정", description = "기존 내 게임 기록을 요청 내용으로 수정합니다. PATCH 이름이지만 요청 DTO의 필수 필드는 전부 전송해야 합니다. 리뷰 변경은 리뷰 전용 API를 사용합니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 기록 수정 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "요청 필드·시간·날짜 검증 실패", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "내 게임 기록 또는 대상 게임 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun updatePlayRecord(
        @PathVariable gameId: Long,
        @Valid @RequestBody reqBody: UserGameReqBody,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameDto> {

        val userGame =
            userGameService.updatePlayRecord(
                userId = user.id,
                gameId = gameId,
                reqBody = reqBody,
            )
        return RsData(
            resultCode = "200-1",
            msg = "게임 기록을 수정했습니다.",
            data = UserGameDto(userGame = userGame),
        )
    }

    @PutMapping("/{gameId}/play-status")
    @Operation(
        summary = "플레이 완료 상태 변경",
        description = "PLAYED, COMPLETED, RETIRED, SHELVED, DROPPED 중 하나를 저장하며, 값을 생략하면 상태를 해제합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponse(responseCode = "200", description = "200-1: 플레이 완료 상태 변경 성공", useReturnTypeSchema = true)
    fun changePlayStatus(
        @PathVariable gameId: Long,
        @RequestParam(required = false) status: PlayStatus?,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGamePlayStatusResponse> {
        val playStatus =
            userGameService.changePlayed(
                userId = user.id,
                gameId = gameId,
                playStatus = status,
            )

        return RsData(
            resultCode = "200-1",
            msg = "플레이 상태를 변경했습니다.",
            data = UserGamePlayStatusResponse(playStatus = playStatus),
        )
    }

    @PatchMapping("/{gameId}/playing")
    @Operation(summary = "플레이 중 상태 변경", description = "쿼리 파라미터 playing=true 또는 false를 전달합니다. 리뷰 본문은 전송하지 않습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponse(responseCode = "200", description = "200-1: 플레이 중 상태 변경 성공", useReturnTypeSchema = true)
    fun changePlaying(
        @PathVariable gameId: Long,
        @RequestParam playing: Boolean,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameStatusResponse> {
        val playingStatus =
            userGameService.changePlaying(
                userId = user.id,
                gameId = gameId,
                playing = playing,
            )

        return RsData(
            resultCode = "200-1",
            msg = "플레이 중 상태를 변경했습니다.",
            data = UserGameStatusResponse(status = playingStatus),
        )
    }

    @PatchMapping("/{gameId}/wishlist")
    @Operation(summary = "위시리스트 상태 변경", description = "쿼리 파라미터 wishlist=true 또는 false를 전달합니다. 리뷰 본문은 전송하지 않습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponse(responseCode = "200", description = "200-1: 위시리스트 상태 변경 성공", useReturnTypeSchema = true)
    fun changeWishlist(
        @PathVariable gameId: Long,
        @RequestParam wishlist: Boolean,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameStatusResponse> {
        val wishlistStatus =
            userGameService.changeWishlist(
                userId = user.id,
                gameId = gameId,
                wishlist = wishlist,
            )

        return RsData(
            resultCode = "200-1",
            msg = "위시리스트 상태를 변경했습니다.",
            data = UserGameStatusResponse(status = wishlistStatus),
        )
    }

    @PatchMapping("/{gameId}/backlog")
    @Operation(summary = "플레이 예정 상태 변경", description = "쿼리 파라미터 backlog=true 또는 false를 전달합니다. 리뷰 본문은 전송하지 않습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponse(responseCode = "200", description = "200-1: 플레이 예정 상태 변경 성공", useReturnTypeSchema = true)
    fun changeBacklog(
        @PathVariable gameId: Long,
        @RequestParam backlog: Boolean,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameStatusResponse> {
        val backlogStatus =
            userGameService.changeBacklog(
                userId = user.id,
                gameId = gameId,
                backlog = backlog,
            )

        return RsData(
            resultCode = "200-1",
            msg = "백로그 상태를 변경했습니다.",
            data = UserGameStatusResponse(status = backlogStatus),
        )
    }

    @PatchMapping("/{gameId}/liked")
    @Operation(
        summary = "좋아하는 게임 상태 변경",
        description = "쿼리 파라미터 liked=true 또는 false로 프로필 Likes 탭의 하트 상태를 변경합니다. 좋아요만으로 라이브러리에 포함되지는 않습니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponse(responseCode = "200", description = "200-1: 좋아하는 게임 상태 변경 성공", useReturnTypeSchema = true)
    fun changeLiked(
        @PathVariable gameId: Long,
        @RequestParam liked: Boolean,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<UserGameStatusResponse> {
        val likedStatus =
            userGameService.changeLiked(
                userId = user.id,
                gameId = gameId,
                liked = liked,
            )

        return RsData(
            resultCode = "200-1",
            msg = "좋아요 상태를 변경했습니다.",
            data = UserGameStatusResponse(status = likedStatus),
        )
    }

    @GetMapping("/recentGames")
    @Operation(summary = "최근 플레이 게임 조회", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponse(responseCode = "200", description = "200-1: 최근 활동 게임 목록", useReturnTypeSchema = true)
    fun recentGameList(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser
    ): RsData<List<UserGameListResponse>> {
        return RsData(
            resultCode = "200-1",
            msg = "최근 플레이한 게임을 불러왔습니다",
            data = userGameService.getRecentPlayedGames(userId = user.id),
        )
    }

}
