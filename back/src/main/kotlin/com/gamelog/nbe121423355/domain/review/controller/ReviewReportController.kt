package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportCreateRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportStatusUpdateRequest
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewReportPageResponse
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewReportResponse
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.service.ReviewReportService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.springframework.data.domain.Sort.Direction.DESC
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "review-report-controller", description = "리뷰 신고 접수 및 관리자 처리 API")
class ReviewReportController constructor(private val reviewReportService: ReviewReportService) {
    @PostMapping("/reviews/{reviewId}/reports")
    @Operation(
        summary = "리뷰 신고 접수",
        description = """
            로그인한 사용자가 공개 중인 다른 사용자의 리뷰를 신고합니다. 사유는 공백이 아닌 1~255자입니다.
            본인의 리뷰는 신고할 수 없으며(400-3), 같은 사용자의 동일 리뷰 중복 신고는 409-1입니다.
            존재하지 않거나 삭제·관리자 숨김 상태인 리뷰는 신고할 수 없습니다(404-3).
            신고는 PENDING으로 접수되고 당시 별점·내용·스포일러 여부를 보존합니다. 이후 리뷰가 수정되어도 신고 당시 값이 우선 표시됩니다.
        """,
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-1: 신고 접수 성공, PENDING 상태 반환", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 사유 누락·공백·255자 초과 / 400-2: 잘못된 JSON / 400-3: 본인 리뷰 신고 불가", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-3: 리뷰 없음 또는 삭제·관리자 숨김 상태 / 404-4: 신고 사용자를 찾을 수 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-1: 같은 사용자가 이미 신고한 리뷰", content = [Content(
            schema = Schema(implementation = RsData::class),
            examples = [ExampleObject(name = "중복 신고", value = """{"resultCode":"409-1","msg":"이미 신고한 리뷰입니다.","data":null}""")],
        )]),
    ])
    fun createReport(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable reviewId: Long,
        @Valid @RequestBody request: @Valid ReviewReportCreateRequest,
    ): RsData<ReviewReportResponse> {
        val response = reviewReportService.createReport(securityUser.id, reviewId, request)
        return RsData<ReviewReportResponse>("201-1", "리뷰 신고가 접수되었습니다.", response)
    }

    @GetMapping("/admin/review-reports")
    @Operation(
        summary = "신고 목록 조회",
        description = """
            로그인한 ADMIN만 조회할 수 있습니다. status를 생략하면 전체 상태를 조회합니다.
            신고를 대상 리뷰 단위로 묶으므로 totalElements·size는 개별 신고 건수가 아닌 신고 대상 리뷰 수 기준입니다.
            status를 지정하면 해당 상태의 신고만 묶어 반환합니다. 각 그룹의 reportCount·pendingReportCount도 반환된 신고 기준입니다.
            그룹은 가장 최근 신고 시각의 내림차순입니다. page는 0부터 시작하고 기본 size는 20입니다.
            정렬은 서비스에서 고정하므로 sort 요청값으로 변경되지 않습니다. 일치하는 신고가 없으면 빈 목록을 반환합니다.
        """,
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-7: 리뷰별 신고 그룹 페이지 조회 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-0: 잘못된 status enum 또는 요청 파라미터 형식", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-1: ADMIN 권한 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun getReports(
        @Parameter(description = "조회할 신고 상태. PENDING(대기), APPROVED(승인), REJECTED(반려). 생략하면 전체 상태를 조회합니다.", example = "PENDING")
        @RequestParam(required = false) status: ReportStatus?,
        @PageableDefault(
            size = 20,
            sort = ["createdDate"],
            direction = Sort.Direction.DESC,
        )
        pageable: Pageable,
    ): RsData<ReviewReportPageResponse> {
        val response = reviewReportService.getReports(status, pageable)
        return RsData<ReviewReportPageResponse>("200-7", "리뷰 신고 목록을 조회했습니다.", response)
    }

    @PutMapping("/admin/review-reports/{reportId}/status")
    @Operation(
        summary = "신고 처리 상태 변경",
        description = """
            로그인한 ADMIN이 선택한 신고와 같은 리뷰에 접수된 모든 PENDING 신고를 일괄 처리합니다.
            요청 status는 APPROVED(승인) 또는 REJECTED(반려)만 가능합니다. PENDING은 처리 결과로 허용하지 않습니다(400-5).
            승인하면 리뷰를 HIDDEN_BY_ADMIN으로 숨겨 공개 조회에서 제외하고 라이브러리 포함 여부를 다시 계산합니다.
            반려하면 신고 상태만 변경하며 리뷰의 기존 공개·숨김 상태는 변경하지 않습니다.
            이미 처리된 신고는 그대로 두고 대기 신고만 변경합니다. 같은 리뷰에 대기 신고가 전혀 없으면 409-4입니다.
            같은 리뷰의 동시 처리 요청은 순서대로 처리하며, 잠금을 얻은 뒤 이미 처리되어 대기 신고가 없으면 409-4를 반환합니다.
            응답은 처리한 대기 신고 중 하나이며, 경로의 reportId와 반드시 같지는 않습니다.
        """,
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-8: 같은 리뷰의 대기 신고 일괄 승인·반려 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: status 누락 / 400-2: 잘못된 JSON·enum / 400-5: PENDING으로 처리할 수 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-1: ADMIN 권한 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-5: 신고를 찾을 수 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-4: 같은 리뷰의 대기 신고가 없어 이미 처리됨", content = [Content(
            schema = Schema(implementation = RsData::class),
            examples = [ExampleObject(name = "이미 처리된 신고", value = """{"resultCode":"409-4","msg":"이미 처리된 리뷰 신고입니다.","data":null}""")],
        )]),
    ])
    fun updateStatus(
        @PathVariable reportId: Long,
        @Valid @RequestBody request: @Valid ReviewReportStatusUpdateRequest,
    ): RsData<ReviewReportResponse> {
        val response = reviewReportService.updateStatus(reportId, request)
        return RsData<ReviewReportResponse>("200-8", "같은 리뷰의 대기 신고 상태를 모두 변경했습니다.", response)
    }
}
