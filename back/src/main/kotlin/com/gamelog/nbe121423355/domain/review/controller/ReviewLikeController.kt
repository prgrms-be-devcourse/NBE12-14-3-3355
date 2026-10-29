package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.dto.response.ReviewLikeResponse
import com.gamelog.nbe121423355.domain.review.service.ReviewLikeService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reviews/{reviewId}/likes")
@Tag(name = "review-like-controller", description = "리뷰 좋아요 등록·취소·조회 API")
class ReviewLikeController(private val reviewLikeService: ReviewLikeService) {
    @PostMapping
    @Operation(summary = "리뷰 좋아요 등록", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-2: 좋아요 등록 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-3: 공개 리뷰 없음 / 404-4: 사용자 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-2: 이미 좋아요를 누른 리뷰", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun addLike(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable reviewId: Long,
    ): RsData<ReviewLikeResponse> {
        val response = reviewLikeService.addLike(securityUser.id, reviewId)
        return RsData<ReviewLikeResponse>("201-2", "리뷰에 좋아요를 등록했습니다.", response)
    }

    @DeleteMapping
    @Operation(summary = "리뷰 좋아요 취소", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-9: 좋아요 취소 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-3: 공개 리뷰 없음 / 404-6: 내 좋아요 기록 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun removeLike(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable reviewId: Long,
    ): RsData<ReviewLikeResponse> {
        val response = reviewLikeService.removeLike(securityUser.id, reviewId)
        return RsData<ReviewLikeResponse>("200-9", "리뷰 좋아요를 취소했습니다.", response)
    }

    @GetMapping("/count")
    @Operation(
        summary = "리뷰 좋아요 정보 조회",
        description = "비로그인 사용자도 좋아요 수를 조회할 수 있으며, 로그인 시 본인의 좋아요 여부도 반환합니다.",
    )
    fun getLikeStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser?,
        @PathVariable reviewId: Long,
    ): RsData<ReviewLikeResponse> {
        val userId = if (securityUser == null) null else securityUser.id
        val response = reviewLikeService.getLikeStatus(userId, reviewId)
        return RsData<ReviewLikeResponse>("200-10", "리뷰 좋아요 정보를 조회했습니다.", response)
    }
}
