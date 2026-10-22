package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.dto.response.CommunityFeedResponse
import com.gamelog.nbe121423355.domain.review.service.CommunityReviewService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/community/reviews")
class CommunityReviewController constructor(private val service: CommunityReviewService) {
    @GetMapping("/following")
    @Operation(summary = "팔로잉 리뷰 피드", security = [SecurityRequirement(name = "bearerAuth")])
    fun following(
        @AuthenticationPrincipal user: SecurityUser,
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
    fun popular(@AuthenticationPrincipal user: SecurityUser): RsData<CommunityFeedResponse> {
        return RsData<CommunityFeedResponse>("200-1", "최근 인기 리뷰를 조회했습니다.", service.popular(user.id))
    }
}
