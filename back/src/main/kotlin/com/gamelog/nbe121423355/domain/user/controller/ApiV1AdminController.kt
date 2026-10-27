package com.gamelog.nbe121423355.domain.user.controller

import com.gamelog.nbe121423355.domain.user.dto.UserDto
import com.gamelog.nbe121423355.domain.user.service.UserService
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "api-v1-admin-controller", description = "관리자 권한 관리 API")
class ApiV1AdminController(private val userService: UserService) {
    @PatchMapping("/users/{userId}/role")
    @Operation(
        summary = "사용자 관리자 승격",
        description = "ADMIN 권한으로 일반 사용자를 관리자로 승격합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "관리자 승격 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-1: ADMIN 권한 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-1: 활성 대상 사용자 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun promoteToAdmin(
        @Parameter(description = "관리자로 승격할 사용자 ID", example = "2") @PathVariable userId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal adminUser: SecurityUser,
    ): RsData<UserDto> {
        val result = userService.promoteToAdmin(userId)
        // 권한 변경 이력 추적용 (누가 누구를 승격했는지)
        log.info("관리자 승격: adminId={}, targetUserId={}", adminUser.id, userId)
        return RsData("200-13", "관리자로 승격 성공", result)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ApiV1AdminController::class.java)
    }
}
