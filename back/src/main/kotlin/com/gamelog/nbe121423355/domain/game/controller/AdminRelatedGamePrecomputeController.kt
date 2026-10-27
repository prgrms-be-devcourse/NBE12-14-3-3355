package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.dto.*
import com.gamelog.nbe121423355.domain.game.service.AdminRelatedGamePrecomputeService
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
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/related-game-precompute")
@Tag(name = "admin-related-game-precompute-controller", description = "관리자 연관 추천 후보 갱신·이력 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses(value = [
    ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
    ApiResponse(responseCode = "403", description = "403-1: ADMIN 권한 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
])
class AdminRelatedGamePrecomputeController(
    private val service: AdminRelatedGamePrecomputeService,
    private val changes: com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeQueryService,
) {
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "연관 추천 후보 갱신 실행 접수", description = "ADMIN 전용 비동기 작업입니다. 202는 완료가 아닌 접수이며 응답 runId로 GET /runs/{runId}에서 상태를 확인합니다. 실행 중인 작업과 경쟁하면 409-10입니다. sourceRunId는 종료된 원본 실행과 동일한 종류·게임 ID·기준선 옵션일 때만 재실행할 수 있습니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "202", description = "202-1: 갱신 실행 접수", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 요청 검증 / 400-2: JSON 오류 / 400-10: 실행 종류·게임 ID·재실행 조건 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-10: 재실행할 원본 이력 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-10: 실행 중·접수된 작업 존재 또는 종료되지 않은 원본 재실행", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun start(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @Valid @RequestBody request: AdminRelatedGamePrecomputeRequest,
    ): RsData<AdminRelatedGamePrecomputeRunResponse> =
        RsData("202-1", "추천 갱신 요청을 접수했습니다.", service.start(user.id, request))

    @GetMapping("/changes")
    @Operation(summary = "추천 입력 변경·갱신 대기 요약", description = "ADMIN 전용입니다. 추천 입력 변경과 갱신 대기 현황을 조회하며 갱신 작업을 시작하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 입력 변경·갱신 대기 현황", useReturnTypeSchema = true)
    fun changes() = RsData("200-1", "추천 입력 변경과 갱신 대기 현황", changes.summary())

    @GetMapping("/changes/targets")
    @Operation(summary = "갱신 대기 게임 목록", description = "ADMIN 전용 페이지 조회입니다. page는 0부터, size 기본값은 20입니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 갱신 대기 게임 페이지", useReturnTypeSchema = true)
    fun pendingTargets(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = RsData("200-1", "갱신 대기 기준 게임", changes.pending(page, size))

    @GetMapping("/changes/preview")
    @Operation(summary = "입력 변경의 영향 게임 미리보기", description = "ADMIN 전용입니다. 변경으로 갱신이 필요한 게임을 미리 조회하며 갱신 작업을 시작하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 변경 영향 게임 페이지", useReturnTypeSchema = true)
    fun previewTargets(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = RsData("200-1", "변경 영향 게임 미리보기", changes.preview(page, size))

    @GetMapping("/status")
    @Operation(summary = "추천 갱신 실행 상태 조회", description = "ADMIN 전용입니다. 현재 실행·잠금 상태를 조회합니다. 실행 접수 후 개별 결과는 runId 상세 조회로 확인합니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 현재 실행·잠금 상태", useReturnTypeSchema = true)
    fun status(): RsData<AdminRelatedGamePrecomputeStatusResponse> =
        RsData("200-1", "추천 갱신 실행 상태", service.status())

    @GetMapping("/runs")
    @Operation(summary = "추천 갱신 이력 조회", description = "ADMIN 전용입니다. page는 0 이상, size는 1~100이며 기본값은 20입니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 실행 이력 페이지", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-10: page·size 범위 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun history(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): RsData<AdminRelatedGamePrecomputePage<AdminRelatedGamePrecomputeRunResponse>> =
        RsData("200-1", "추천 갱신 실행 이력", service.history(page, size))

    @GetMapping("/runs/{runId}")
    @Operation(summary = "추천 갱신 실행 상세 조회", description = "ADMIN 전용입니다. 접수 응답에서 받은 runId를 사용해 진행·종료 상태와 처리 결과를 확인합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 실행 상세", useReturnTypeSchema = true),
        ApiResponse(responseCode = "404", description = "404-10: 실행 이력 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun detail(@PathVariable runId: String): RsData<AdminRelatedGamePrecomputeRunResponse> =
        RsData("200-1", "추천 갱신 실행 상세", service.detail(runId))

    @GetMapping("/runs/{runId}/failures")
    @Operation(summary = "추천 갱신 실패 게임 조회", description = "ADMIN 전용입니다. 해당 실행에서 실패한 게임을 조회합니다. page는 0 이상, size는 1~100입니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 실패 게임 페이지", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-10: page·size 범위 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-10: 실행 이력 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun failures(
        @PathVariable runId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): RsData<AdminRelatedGamePrecomputePage<AdminRelatedGamePrecomputeFailureResponse>> =
        RsData("200-1", "추천 갱신 실패 상세", service.failures(runId, page, size))
}
