package com.gamelog.nbe121423355.domain.notification.controller

import com.gamelog.nbe121423355.domain.notification.dto.NotificationPageResponse
import com.gamelog.nbe121423355.domain.notification.dto.NotificationResponse
import com.gamelog.nbe121423355.domain.notification.service.NotificationService
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "notification-controller", description = "내 알림 목록·읽음 처리 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses(value = [ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))])])
class NotificationController(private val service: NotificationService) {
    @GetMapping
    @Operation(summary = "내 알림 목록 조회", description = "내 알림을 최대 20개씩 커서 방식으로 조회합니다. 첫 요청에는 before를 생략하고, 다음 요청에는 응답의 nextCursor를 전달합니다. nextCursor가 null이면 마지막 페이지입니다. unreadOnly=true이면 미확인 알림만 조회합니다. 대상이 삭제·숨김된 알림은 url이 null일 수 있으므로 unavailable도 확인하세요.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 알림 목록 조회 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-30: before가 1 미만 / 400-0: 파라미터 형식 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @Parameter(description = "이전 응답의 nextCursor. 첫 조회는 생략합니다.", example = "100")
        @RequestParam(required = false) before: Long?,
        @Parameter(description = "미확인 알림만 조회할지 여부", example = "false")
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    ): RsData<NotificationPageResponse> =
        RsData("200-1", "알림 목록입니다.", service.list(user.id, before, unreadOnly))

    @GetMapping("/unread-count")
    @Operation(summary = "미확인 알림 수 조회", description = "로그인한 사용자 본인의 미확인 알림 수를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 미확인 알림 개수", useReturnTypeSchema = true)
    fun count(@Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser): RsData<Long> =
        RsData("200-1", "미확인 알림 수입니다.", service.unread(user.id))

    @PutMapping("/{id}/read")
    @Operation(summary = "내 알림 읽음 처리", description = "본인이 수신한 알림만 처리합니다. 이미 읽은 알림도 다시 요청할 수 있습니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 읽음 처리된 알림", useReturnTypeSchema = true),
        ApiResponse(responseCode = "404", description = "404-30: 알림 없음 또는 다른 사용자의 알림", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun read(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @Parameter(description = "읽음 처리할 알림 ID", example = "123") @PathVariable id: Long,
    ): RsData<NotificationResponse> =
        RsData("200-1", "알림을 읽었습니다.", service.read(user.id, id))

    @PutMapping("/read-all")
    @Operation(summary = "내 알림 전체 읽음 처리", description = "본인의 미확인 알림을 모두 읽음 처리합니다. 처리할 알림이 없어도 성공합니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 전체 읽음 처리 성공", useReturnTypeSchema = true)
    fun readAll(@Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser): RsData<Void> {
        service.readAll(user.id)
        return RsData("200-1", "모든 알림을 읽었습니다.")
    }
}
