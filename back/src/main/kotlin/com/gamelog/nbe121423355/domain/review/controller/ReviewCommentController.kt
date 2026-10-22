package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.service.ReviewCommentService
import com.gamelog.nbe121423355.domain.review.service.ReviewCommentService.*
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/reviews/{reviewId}/comments")
class ReviewCommentController constructor(private val service: ReviewCommentService) {
    @GetMapping
    fun list(
        @PathVariable reviewId: Long,
        @RequestParam(required = false) parentId: Long?,
        @RequestParam(required = false) after: Long?,
        @AuthenticationPrincipal user: SecurityUser?,
    ): RsData<ReviewCommentService.Page> {
        return RsData<ReviewCommentService.Page>(
            "200-1",
            "댓글을 조회했습니다.",
            service.list(reviewId, if (user == null) null else user.id, parentId, after),
        )
    }

    @GetMapping("/count")
    fun count(@PathVariable reviewId: Long): RsData<Long> {
        return RsData("200-1", "댓글 수를 조회했습니다.", service.count(reviewId))
    }

    @PostMapping
    fun create(
        @PathVariable reviewId: Long,
        @AuthenticationPrincipal user: SecurityUser,
        @RequestBody body: ReviewCommentService.Body,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "201-1",
            "댓글을 작성했습니다.",
            service.create(reviewId, user.id, body),
        )
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @AuthenticationPrincipal user: SecurityUser?,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "200-1",
            "댓글을 조회했습니다.",
            service.get(reviewId, id, if (user == null) null else user.id),
        )
    }

    @PutMapping("/{id}")
    fun edit(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @AuthenticationPrincipal user: SecurityUser,
        @RequestBody body: ReviewCommentService.Body,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "200-1",
            "댓글을 수정했습니다.",
            service.edit(reviewId, id, user.id, body),
        )
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @AuthenticationPrincipal user: SecurityUser,
    ): RsData<Void> {
        service.delete(reviewId, id, user.id)
        return RsData<Void>("200-1", "댓글을 삭제했습니다.", null)
    }

    @PutMapping("/{id}/likes")
    fun like(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @AuthenticationPrincipal user: SecurityUser,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "200-1",
            "좋아요를 등록했습니다.",
            service.like(reviewId, id, user.id, true),
        )
    }

    @DeleteMapping("/{id}/likes")
    fun unlike(
        @PathVariable reviewId: Long,
        @PathVariable id: Long,
        @AuthenticationPrincipal user: SecurityUser,
    ): RsData<ReviewCommentService.Item> {
        return RsData<ReviewCommentService.Item>(
            "200-1",
            "좋아요를 취소했습니다.",
            service.like(reviewId, id, user.id, false),
        )
    }
}
