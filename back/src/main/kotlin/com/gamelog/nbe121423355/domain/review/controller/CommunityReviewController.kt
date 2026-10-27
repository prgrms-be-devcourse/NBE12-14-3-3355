package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.dto.response.CommunityFeedResponse
import com.gamelog.nbe121423355.domain.review.service.CommunityReviewService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/community/reviews")
@Tag(name = "community-review-controller", description = "로그인 사용자 커뮤니티 리뷰 피드")
@ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))])
class CommunityReviewController constructor(private val service: CommunityReviewService) {
    @GetMapping("/following")
    @Operation(summary = "팔로잉 리뷰 피드", description = "로그인한 사용자가 팔로우하는 사용자의 공개 리뷰 피드를 조회합니다. page는 0부터 시작합니다.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponse(responseCode = "200", description = "200-1: 팔로잉 리뷰 피드", useReturnTypeSchema = true)
    fun following(
        @Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser,
        @RequestParam(defaultValue = "0") page: Int,
    ): RsData<CommunityFeedResponse> {
        return RsData<CommunityFeedResponse>(
            "200-1",
            "팔로잉 리뷰를 조회했습니다.",
            service.following(user.id, page),
        )
    }

    @GetMapping("/popular")
    @Operation(
        summary = "최근 168시간에 작성된 인기 리뷰",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponse(responseCode = "200", description = "200-1: 최근 인기 리뷰 피드", useReturnTypeSchema = true)
    fun popular(@Parameter(hidden = true) @AuthenticationPrincipal user: SecurityUser): RsData<CommunityFeedResponse> {
        return RsData<CommunityFeedResponse>("200-1", "최근 인기 리뷰를 조회했습니다.", service.popular(user.id))
    }
}
