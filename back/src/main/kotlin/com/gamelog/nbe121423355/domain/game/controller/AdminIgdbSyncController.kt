package com.gamelog.nbe121423355.domain.game.controller

import com.gamelog.nbe121423355.domain.game.sync.*
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncService
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncState
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncStore
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
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/igdb-sync")
@Tag(name = "admin-igdb-sync-controller", description = "관리자 게임 데이터 동기화 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses(value = [
    ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
    ApiResponse(responseCode = "403", description = "403-1: ADMIN 권한 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
])
class AdminIgdbSyncController
constructor(private val service: IgdbSyncService, private val store: IgdbSyncStore) {
    @GetMapping
    @Operation(summary = "IGDB 동기화 상태 조회", description = "ADMIN 전용입니다. 동기화 상태와 진행·성공 정보를 조회합니다. 실행 접수 후 이 API로 완료 여부를 확인합니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 동기화 상태", useReturnTypeSchema = true)
    fun status(): RsData<IgdbSyncState> {
        return RsData<IgdbSyncState>("200-1", "동기화 상태", store.status())
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "IGDB 동기화 실행 접수", description = "ADMIN 전용 비동기 작업입니다. 202는 접수 성공이며 데이터 수집 완료를 의미하지 않습니다. GET 상태 조회로 결과를 확인하세요.")
    @ApiResponse(responseCode = "202", description = "202-1: 동기화 실행 접수", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "409", description = "409-1: 이미 동기화가 실행 중임", content = [Content(schema = Schema(implementation = RsData::class))])
    fun start(@Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser): RsData<IgdbSyncState> {
        return RsData<IgdbSyncState>("202-1", "동기화를 시작했습니다.", service.start(user.id))
    }
}
