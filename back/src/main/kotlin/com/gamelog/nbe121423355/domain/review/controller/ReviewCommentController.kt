package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.service.ReviewCommentService
import com.gamelog.nbe121423355.domain.review.service.ReviewCommentService.*
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
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/reviews/{reviewId}/comments")
@Tag(name = "review-comment-controller", description = "리뷰 댓글·답글·댓글 좋아요 API")
@ApiResponse(responseCode = "404", description = "404-3: 공개 리뷰 없음 / 404-20: 해당 리뷰의 댓글 없음", content = [Content(schema = Schema(implementation = RsData::class))])
class ReviewCommentController(private val service: ReviewCommentService) {
    @GetMapping
    @Operation(summary = "리뷰 댓글·답글 목록", description = "비로그인 조회가 가능합니다. 원댓글은 최대 20개, parentId를 지정한 답글은 최대 10개씩 조회합니다. 다음 페이지는 nextCursor를 after에 전달하고, null이면 마지막입니다. 답글의 답글은 조회할 수 없습니다. 로그인하면 mine·liked 등 내 상태도 반영합니다.")
    @ApiResponse(responseCode = "400", description = "400-20: 잘못된 커서 또는 원댓글이 아닌 parentId", content = [Content(schema = Schema(implementation = RsData::class))])
    @ApiResponse(responseCode = "200", description = "200-1: 댓글·답글 커서 페이지", useReturnTypeSchema = true)
    fun list(
        @PathVariable reviewId: Long,
        @Parameter(description = "답글을 조회할 원댓글 ID. 원댓글 목록은 생략") @RequestParam(required = false) parentId: Long?,
        @Parameter(description = "이전 응답의 nextCursor. 첫 조회는 생략") @RequestParam(required = false) after: Long?,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser?,
    ): RsData<ReviewCommentService.Page> {
        return RsData<ReviewCommentService.Page>(
            "200-1",
            "댓글을 조회했습니다.",
            service.list(reviewId, if (user == null) null else user.id, parentId, after),
        )
    }

    @GetMapping("/count")
    @Operation(summary = "리뷰의 유효 댓글 수", description = "비로그인 조회가 가능합니다. 삭제되지 않은 댓글·답글 수를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 유효 댓글·답글 개수", useReturnTypeSchema = true)
    fun count(@PathVariable reviewId: Long): RsData<Long> {
        return RsData("200-1", "댓글 수를 조회했습니다.", service.count(reviewId))
    }

    @PostMapping
    @Operation(summary = "리뷰 댓글·답글 작성", description = "로그인이 필요합니다. content는 공백이 아닌 1~1000자입니다. parentId를 생략하면 원댓글, 지정하면 해당 원댓글의 답글입니다. 답글에는 답글을 작성할 수 없습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-1: 댓글 작성 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-20: 내용 또는 답글 깊이 오류 / 400-2: JSON 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun create(
        @PathVariable reviewId: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @RequestBody body: ReviewCommentService.Body,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "201-1",
            "댓글을 작성했습니다.",
            service.create(reviewId, user.id, body),
        )
    }

    @GetMapping("/{id}")
    @Operation(summary = "리뷰 댓글 단건 조회", description = "비로그인 조회가 가능합니다. 삭제된 댓글은 내용·작성자 정보를 가린 표시용 항목으로 반환할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "200-1: 댓글 단건", useReturnTypeSchema = true)
    fun get(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser?,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "200-1",
            "댓글을 조회했습니다.",
            service.get(reviewId, id, if (user == null) null else user.id),
        )
    }

    @PutMapping("/{id}")
    @Operation(summary = "내 댓글 수정", description = "본인의 유효 댓글만 수정할 수 있습니다. content는 공백이 아닌 1~1000자이며 parentId로 댓글의 부모를 바꾸지 않습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "400", description = "400-20: 댓글 내용 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-20: 본인의 댓글만 수정 가능", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    @ApiResponse(responseCode = "200", description = "200-1: 수정된 댓글", useReturnTypeSchema = true)
    fun edit(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @RequestBody body: ReviewCommentService.Body,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "200-1",
            "댓글을 수정했습니다.",
            service.edit(reviewId, id, user.id, body),
        )
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "내 댓글 삭제", description = "본인의 유효 댓글만 삭제할 수 있습니다. 댓글 내용은 삭제 표시로 전환되고 해당 댓글의 좋아요는 제거됩니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-20: 본인의 댓글만 삭제 가능", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    @ApiResponse(responseCode = "200", description = "200-1: 댓글 삭제 성공", useReturnTypeSchema = true)
    fun delete(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<Void> {
        service.delete(reviewId, id, user.id)
        return RsData<Void>("200-1", "댓글을 삭제했습니다.", null)
    }

    @PutMapping("/{id}/likes")
    @Operation(summary = "댓글 좋아요 등록", description = "로그인이 필요합니다. 이미 등록한 좋아요를 다시 등록해도 중복 저장하지 않습니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))])
    @ApiResponse(responseCode = "200", description = "200-1: 좋아요 반영된 댓글", useReturnTypeSchema = true)
    fun like(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "200-1",
            "좋아요를 등록했습니다.",
            service.like(reviewId, id, user.id, true),
        )
    }

    @DeleteMapping("/{id}/likes")
    @Operation(summary = "댓글 좋아요 취소", description = "로그인이 필요합니다. 공개 리뷰의 유효 댓글에 대한 내 좋아요를 취소합니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))])
    @ApiResponse(responseCode = "200", description = "200-1: 좋아요 취소된 댓글", useReturnTypeSchema = true)
    fun unlike(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "200-1",
            "좋아요를 취소했습니다.",
            service.like(reviewId, id, user.id, false),
        )
    }
}
