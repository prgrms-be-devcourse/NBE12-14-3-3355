package com.gamelog.nbe121423355.domain.notification

import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val service: NotificationService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: SecurityUser,
        @RequestParam(required = false) before: Long?,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
    ): RsData<NotificationService.Page> = RsData("200-1", "알림 목록입니다.", service.list(user.id, before, unreadOnly))
    @GetMapping("/unread-count")
    fun count(@AuthenticationPrincipal user: SecurityUser): RsData<Long> = RsData("200-1", "미확인 알림 수입니다.", service.unread(user.id))
    @PutMapping("/{id}/read")
    fun read(@AuthenticationPrincipal user: SecurityUser, @PathVariable id: Long): RsData<NotificationService.Item> = RsData("200-1", "알림을 읽었습니다.", service.read(user.id, id))
    @PutMapping("/read-all")
    fun readAll(@AuthenticationPrincipal user: SecurityUser): RsData<Void> { service.readAll(user.id); return RsData("200-1", "모든 알림을 읽었습니다.") }
}
