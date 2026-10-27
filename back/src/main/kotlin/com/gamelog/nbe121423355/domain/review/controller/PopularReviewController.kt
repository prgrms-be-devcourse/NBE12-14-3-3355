package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.dto.response.PopularReviewResponse
import com.gamelog.nbe121423355.domain.review.service.PopularReviewService
import com.gamelog.nbe121423355.global.dto.RsData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reviews")
@Tag(name = "popular-review-controller", description = "인기 리뷰 조회 API")
class PopularReviewController(private val popularReviewService: PopularReviewService) {
    @GetMapping("/popular")
    @Operation(summary = "인기 리뷰 조회", description = "비로그인 조회가 가능합니다. 좋아요 수가 많은 공개 리뷰를 조회하며 size는 기본 5, 1~50입니다. 최근 168시간 기준의 로그인 커뮤니티 인기 피드와는 별개입니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 인기 리뷰 목록", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-10: size가 1~50 밖 / 400-0: 숫자 형식 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun getPopularReviews(
        @Parameter(
            description = "조회할 리뷰 수",
            example = "5",
        )
        @RequestParam(defaultValue = "5")
        size: Int
    ): RsData<List<PopularReviewResponse>> {
        val response = popularReviewService.getPopularReviews(size)
        return RsData("200-1", "인기 리뷰를 조회했습니다.", response)
    }
}
