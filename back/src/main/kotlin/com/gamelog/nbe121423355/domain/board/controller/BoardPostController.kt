package com.gamelog.nbe121423355.domain.board.controller

import com.gamelog.nbe121423355.domain.board.dto.BoardPostPageResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardPostRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardPostResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardReportRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardReportResponse
import com.gamelog.nbe121423355.domain.board.service.BoardPostService
import com.gamelog.nbe121423355.domain.board.service.BoardReportService
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
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/community/posts")
@Tag(name = "board-post-controller", description = "자유게시판 게시글·좋아요·신고 API")
@ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))])
class BoardPostController(
    private val postService: BoardPostService,
    private val reportService: BoardReportService,
) {
    @GetMapping
    @Operation(
        summary = "자유게시판 목록",
        description = "로그인이 필요합니다. 최신순으로 20개씩 조회합니다. 다음 페이지는 nextCursor를 after에 전달하고, null이면 마지막입니다. q를 지정하면 제목·본문에서 검색합니다. 삭제·숨김된 글과 탈퇴한 사용자의 글은 제외합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-40: 게시글 커서 페이지", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-40: 잘못된 커서 또는 50자 초과 검색어 / 400-0: 파라미터 형식 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @Parameter(description = "제목·본문 검색어. 최대 50자", example = "협동") @RequestParam(required = false) q: String?,
        @Parameter(description = "이전 응답의 nextCursor. 첫 조회는 생략") @RequestParam(required = false) after: Long?,
    ): RsData<BoardPostPageResponse> =
        RsData("200-40", "게시글 목록을 조회했습니다.", postService.list(user.id, q, after))

    @PostMapping
    @Operation(
        summary = "자유게시글 작성",
        description = "로그인이 필요합니다. 제목은 공백이 아닌 1~100자, 본문은 공백이 아닌 1~5000자입니다. gameId로 게임을 1개까지 첨부할 수 있습니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-40: 게시글 작성 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 제목·본문 검증 실패 / 400-2: JSON 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-43: 첨부할 게임 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun create(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @Valid @RequestBody request: BoardPostRequest,
    ): RsData<BoardPostResponse> =
        RsData("201-40", "게시글을 작성했습니다.", postService.create(user.id, request))

    @GetMapping("/{postId}")
    @Operation(summary = "자유게시글 단건 조회", description = "로그인이 필요합니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-41: 게시글 단건", useReturnTypeSchema = true),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음 (삭제·숨김·작성자 탈퇴 포함)", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun get(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
    ): RsData<BoardPostResponse> =
        RsData("200-41", "게시글을 조회했습니다.", postService.get(user.id, postId))

    @PutMapping("/{postId}")
    @Operation(
        summary = "내 자유게시글 수정",
        description = "본인의 글만 수정할 수 있습니다. 요청한 값으로 전체를 바꾸므로 gameId를 생략하면 첨부 게임이 제거됩니다. 수정해도 목록 순서는 바뀌지 않습니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-42: 수정된 게시글", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 제목·본문 검증 실패 / 400-2: JSON 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-40: 본인의 글만 수정 가능", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음 / 404-43: 첨부할 게임 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
        @Valid @RequestBody request: BoardPostRequest,
    ): RsData<BoardPostResponse> =
        RsData("200-42", "게시글을 수정했습니다.", postService.update(user.id, postId, request))

    @DeleteMapping("/{postId}")
    @Operation(summary = "내 자유게시글 삭제", description = "본인의 글만 삭제할 수 있습니다. 삭제한 글은 댓글과 함께 더 이상 조회되지 않습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-43: 게시글 삭제 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "403", description = "403-40: 본인의 글만 삭제 가능", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
    ): RsData<Void> {
        postService.delete(user.id, postId)
        return RsData("200-43", "게시글을 삭제했습니다.")
    }

    @PutMapping("/{postId}/likes")
    @Operation(summary = "자유게시글 좋아요", description = "로그인이 필요합니다. 여러 번 요청해도 좋아요는 하나만 저장됩니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-44: 좋아요가 반영된 게시글", useReturnTypeSchema = true),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun like(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
    ): RsData<BoardPostResponse> =
        RsData("200-44", "게시글에 좋아요를 눌렀습니다.", postService.like(user.id, postId, true))

    @DeleteMapping("/{postId}/likes")
    @Operation(summary = "자유게시글 좋아요 취소", description = "로그인이 필요합니다. 좋아요가 없어도 성공합니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-45: 좋아요가 취소된 게시글", useReturnTypeSchema = true),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun unlike(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
    ): RsData<BoardPostResponse> =
        RsData("200-45", "게시글 좋아요를 취소했습니다.", postService.like(user.id, postId, false))

    @PostMapping("/{postId}/reports")
    @Operation(summary = "자유게시글 신고", description = "로그인이 필요합니다. 본인 글은 신고할 수 없고 같은 글은 한 번만 신고할 수 있습니다. 신고 당시 제목·본문을 보존합니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-42: 신고 접수", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 사유 검증 실패 / 400-42: 본인 글 신고", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-40: 이미 신고함", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun report(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
        @Valid @RequestBody request: BoardReportRequest,
    ): RsData<BoardReportResponse> =
        RsData("201-42", "신고를 접수했습니다.", reportService.reportPost(user.id, postId, request))
}
