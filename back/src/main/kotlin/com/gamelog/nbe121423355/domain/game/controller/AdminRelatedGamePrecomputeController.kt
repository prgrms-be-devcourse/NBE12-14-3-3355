package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.dto.*
import com.gamelog.nbe121423355.domain.game.service.AdminRelatedGamePrecomputeService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/related-game-precompute")
class AdminRelatedGamePrecomputeController(
    private val service: AdminRelatedGamePrecomputeService,
    private val changes: com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeQueryService,
) {
    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(
        @AuthenticationPrincipal user: SecurityUser,
        @Valid @RequestBody request: AdminRelatedGamePrecomputeRequest,
    ): RsData<AdminRelatedGamePrecomputeRunResponse> =
        RsData("202-1", "추천 갱신 요청을 접수했습니다.", service.start(user.id, request))

    @GetMapping("/changes")
    fun changes() = RsData("200-1", "추천 입력 변경과 갱신 대기 현황", changes.summary())

    @GetMapping("/changes/targets")
    fun pendingTargets(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ) = RsData("200-1", "갱신 대기 기준 게임", changes.pending(page, size))

    @GetMapping("/status")
    fun status(): RsData<AdminRelatedGamePrecomputeStatusResponse> =
        RsData("200-1", "추천 갱신 실행 상태", service.status())

    @GetMapping("/runs")
    fun history(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): RsData<AdminRelatedGamePrecomputePage<AdminRelatedGamePrecomputeRunResponse>> =
        RsData("200-1", "추천 갱신 실행 이력", service.history(page, size))

    @GetMapping("/runs/{runId}")
    fun detail(@PathVariable runId: String): RsData<AdminRelatedGamePrecomputeRunResponse> =
        RsData("200-1", "추천 갱신 실행 상세", service.detail(runId))

    @GetMapping("/runs/{runId}/failures")
    fun failures(
        @PathVariable runId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): RsData<AdminRelatedGamePrecomputePage<AdminRelatedGamePrecomputeFailureResponse>> =
        RsData("200-1", "추천 갱신 실패 상세", service.failures(runId, page, size))
}
