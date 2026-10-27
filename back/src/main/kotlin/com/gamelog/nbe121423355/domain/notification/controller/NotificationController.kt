package com.gamelog.nbe121423355.domain.notification.controller

import com.gamelog.nbe121423355.domain.notification.dto.NotificationPageResponse
import com.gamelog.nbe121423355.domain.notification.dto.NotificationResponse
import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
@Tag(name = "notification-controller", description = "알림 조회·읽음 처리 API")
class NotificationController(private val service: NotificationService) {
    @GetMapping
    @Operation(
        summary = "내 알림 목록 조회",
        description = "최신 알림부터 최대 20개를 조회합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @Parameter(description = "이 ID보다 이전 알림 조회. 첫 페이지는 생략하며 1 이상이어야 합니다.", example = "123")
        @RequestParam(required = false) before: Long?,
        @Parameter(description = "true이면 읽지 않은 알림만 조회", example = "false")
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    ): RsData<NotificationPageResponse> =
        RsData("200-1", "알림 목록입니다.", service.list(user.id, before, unreadOnly))

    @GetMapping("/unread-count")
    @Operation(
        summary = "내 미읽음 알림 수 조회",
        description = "로그인 사용자의 읽지 않은 알림 수를 반환합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun count(@Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser): RsData<Long> =
        RsData("200-1", "미확인 알림 수입니다.", service.unread(user.id))

    @PutMapping("/{id}/read")
    @Operation(
        summary = "내 알림 읽음 처리",
        description = "본인 알림을 읽음 처리합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun read(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @Parameter(description = "읽음 처리할 알림 ID", example = "123") @PathVariable id: Long,
    ): RsData<NotificationResponse> =
        RsData("200-1", "알림을 읽었습니다.", service.read(user.id, id))

    @PutMapping("/read-all")
    @Operation(
        summary = "내 알림 전체 읽음 처리",
        description = "로그인 사용자의 미읽음 알림을 모두 읽음 처리합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun readAll(@Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser): RsData<Void> {
        service.readAll(user.id)
        return RsData("200-1", "모든 알림을 읽었습니다.")
    }
}
