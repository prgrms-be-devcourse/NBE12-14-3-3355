package com.gamelog.nbe121423355.domain.board.controller

import com.gamelog.nbe121423355.domain.board.dto.BoardReportPageResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardReportResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardReportStatusRequest
import com.gamelog.nbe121423355.domain.board.service.BoardReportService
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.global.dto.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/board-reports")
@Tag(name = "admin-board-report-controller", description = "관리자 자유게시판 신고 처리 API")
@ApiResponses(value = [
    ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
    ApiResponse(responseCode = "403", description = "403-1: ADMIN 권한 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
])
class AdminBoardReportController(private val reportService: BoardReportService) {
    @GetMapping
    @Operation(
        summary = "자유게시판 신고 목록",
        description = "ADMIN만 조회할 수 있습니다. 최근 신고순이며 status를 생략하면 전체 상태를 조회합니다. 신고 당시 원문(snapshot)을 함께 반환합니다. page는 0부터 시작하고 기본 size는 20입니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-49: 신고 페이지", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-0: 잘못된 status 값", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun list(
        @Parameter(description = "PENDING(대기), APPROVED(승인), REJECTED(반려). 생략하면 전체", example = "PENDING")
        @RequestParam(required = false) status: ReportStatus?,
        @Parameter(hidden = true) @PageableDefault(size = 20) pageable: Pageable,
    ): RsData<BoardReportPageResponse> =
        RsData("200-49", "자유게시판 신고 목록을 조회했습니다.", reportService.list(status, pageable))

    @PutMapping("/{reportId}/status")
    @Operation(
        summary = "자유게시판 신고 처리",
        description = "ADMIN만 처리할 수 있습니다. 선택한 신고와 같은 대상의 대기 신고를 모두 함께 승인·반려합니다. 승인하면 대상 글·댓글을 숨깁니다. 응답은 처리한 신고 중 하나이며 경로의 reportId와 다를 수 있습니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-50: 같은 대상의 대기 신고 일괄 처리 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: status 누락 / 400-2: 잘못된 JSON·enum / 400-43: PENDING으로 처리 불가", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-42: 신고 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-41: 같은 대상의 대기 신고가 없어 이미 처리됨", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun updateStatus(
        @PathVariable reportId: Long,
        @Valid @RequestBody request: BoardReportStatusRequest,
    ): RsData<BoardReportResponse> =
        RsData("200-50", "같은 대상의 대기 신고를 처리했습니다.", reportService.updateStatus(reportId, request))
}
