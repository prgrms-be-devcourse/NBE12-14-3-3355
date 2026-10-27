package com.gamelog.nbe121423355.domain.board.controller

import com.gamelog.nbe121423355.domain.board.dto.BoardCommentPageResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardCommentRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardCommentResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardCommentUpdateRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardReportRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardReportResponse
import com.gamelog.nbe121423355.domain.board.service.BoardCommentService
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
@RequestMapping("/api/v1/community/posts/{postId}/comments")
@Tag(name = "board-comment-controller", description = "자유게시판 댓글·답글·신고 API")
@ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))])
class BoardCommentController(
    private val commentService: BoardCommentService,
    private val reportService: BoardReportService,
) {
    @GetMapping
    @Operation(
        summary = "자유게시글 댓글·답글 목록",
        description = "로그인이 필요합니다. 오래된 순으로 원댓글은 3개, parentId를 지정한 답글은 5개씩 조회합니다. 다음 페이지는 nextCursor를 after에 전달하고, null이면 마지막입니다. 삭제·숨김된 원댓글은 남은 답글이 있을 때만 removed=true로 자리를 유지합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-46: 댓글·답글 커서 페이지", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-40: 잘못된 커서 / 400-41: 원댓글이 아닌 parentId", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음 / 404-41: 원댓글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
        @Parameter(description = "답글을 조회할 원댓글 ID. 원댓글 목록은 생략") @RequestParam(required = false) parentId: Long?,
        @Parameter(description = "이전 응답의 nextCursor. 첫 조회는 생략") @RequestParam(required = false) after: Long?,
    ): RsData<BoardCommentPageResponse> =
        RsData("200-46", "댓글을 조회했습니다.", commentService.list(user.id, postId, parentId, after))

    @PostMapping
    @Operation(
        summary = "자유게시글 댓글·답글 작성",
        description = "로그인이 필요합니다. 내용은 공백이 아닌 1~1000자입니다. parentId를 지정하면 답글이며, 답글에는 답글을 달 수 없습니다. 글 작성자와 원댓글 작성자에게 알림을 보냅니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-41: 댓글 작성 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 내용 검증 실패 / 400-2: JSON 오류 / 400-41: 답글의 답글 또는 삭제된 댓글에 답글", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음 / 404-41: 원댓글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun create(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
        @Valid @RequestBody request: BoardCommentRequest,
    ): RsData<BoardCommentResponse> =
        RsData("201-41", "댓글을 작성했습니다.", commentService.create(user.id, postId, request))

    @PutMapping("/{commentId}")
    @Operation(summary = "내 댓글 수정", description = "본인의 댓글만 수정할 수 있습니다. 원댓글·답글 관계는 바뀌지 않습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-47: 수정된 댓글", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 내용 검증 실패 / 400-2: JSON 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-40: 본인의 댓글만 수정 가능", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음 / 404-41: 댓글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
        @Valid @RequestBody request: BoardCommentUpdateRequest,
    ): RsData<BoardCommentResponse> =
        RsData("200-47", "댓글을 수정했습니다.", commentService.update(user.id, postId, commentId, request))

    @DeleteMapping("/{commentId}")
    @Operation(summary = "내 댓글 삭제", description = "본인의 댓글만 삭제할 수 있습니다. 답글이 남은 원댓글은 '삭제된 댓글입니다'로 자리를 유지합니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-48: 댓글 삭제 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "403", description = "403-40: 본인의 댓글만 삭제 가능", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음 / 404-41: 댓글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
    ): RsData<Void> {
        commentService.delete(user.id, postId, commentId)
        return RsData("200-48", "댓글을 삭제했습니다.")
    }

    @PostMapping("/{commentId}/reports")
    @Operation(summary = "댓글·답글 신고", description = "로그인이 필요합니다. 본인 댓글은 신고할 수 없고 같은 댓글은 한 번만 신고할 수 있습니다. 신고 당시 내용을 보존합니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-42: 신고 접수", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 사유 검증 실패 / 400-42: 본인 댓글 신고", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-40: 게시글 없음 / 404-41: 댓글 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-40: 이미 신고함", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun report(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
        @Valid @RequestBody request: BoardReportRequest,
    ): RsData<BoardReportResponse> =
        RsData("201-42", "신고를 접수했습니다.", reportService.reportComment(user.id, postId, commentId, request))
}
