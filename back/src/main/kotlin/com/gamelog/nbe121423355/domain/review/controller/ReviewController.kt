package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.response.DetailedReviewResponse
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewPageResponse
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewResponse
import com.gamelog.nbe121423355.domain.review.service.GameRecordReviewService
import com.gamelog.nbe121423355.domain.review.service.ReviewService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.springframework.data.domain.Sort.Direction.DESC
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "review-controller", description = "게임 리뷰 작성·조회·수정·삭제 API")
class ReviewController
constructor(
    private val reviewService: ReviewService,
    private val gameRecordReviewService: GameRecordReviewService,
) {
    @PutMapping("/games/{gameId}/reviews")
    @Operation(
        summary = "게임 기록 및 리뷰 통합 저장",
        description = """
            로그인한 사용자의 게임 기록과 리뷰를 하나의 트랜잭션으로 저장합니다.
            userGame은 필수입니다. review가 null이거나 별점·내용이 모두 비어 있으면 기존 리뷰를 유지하며 삭제하지 않습니다.
            기존 활성 리뷰를 변경할 때는 조회한 review.version이 필요합니다. 새 리뷰·사용자가 삭제한 리뷰의 재작성 시에는 버전을 생략합니다.
            삭제 후 재작성에서 버전을 보내면 현재 저장된 버전과 일치해야 하며, 관리자 숨김 리뷰는 재작성할 수 없습니다.
            409-6이면 최신 내용을 다시 조회하고 사용자 변경 의사를 확인한 뒤 재시도합니다. 자동 재전송하지 마세요.
            리뷰 저장이 실패하면 함께 변경한 게임 기록도 롤백됩니다. 상태·리뷰만 바꿀 때는 각각의 전용 API를 사용합니다.
        """,
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-7: 게임 기록·리뷰 통합 저장 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 필수 게임 기록·별점·버전 등 입력 검증 실패 / 400-2: 잘못된 JSON 형식", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "대상 게임 또는 사용자를 찾을 수 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-5: 관리자 숨김 리뷰 재작성 불가 / 409-6: 기존 리뷰 버전 누락·불일치 또는 동시 수정 충돌", content = [Content(
            schema = Schema(implementation = RsData::class),
            examples = [ExampleObject(name = "리뷰 버전 충돌", value = REVIEW_VERSION_CONFLICT_EXAMPLE)],
        )]),
    ])
    fun saveDetailedReview(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable gameId: Long,
        @Valid @RequestBody request: @Valid DetailedReviewSaveRequest,
    ): RsData<DetailedReviewResponse> {
        val response = gameRecordReviewService.saveDetailedReview(securityUser.id, gameId, request)
        return RsData<DetailedReviewResponse>("200-7", "상세 리뷰가 저장되었습니다.", response)
    }

    @GetMapping("/games/{gameId}/reviews/me")
    @Operation(
        summary = "내 게임 기록 및 리뷰 조회",
        description = "로그인한 사용자의 게임 기록과 리뷰를 조회합니다. 저장한 기록이 없으면 userGame·review는 null입니다. 기존 리뷰를 수정·삭제할 때는 응답의 review.version을 전달합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-8: 내 기록·리뷰 조회 성공. 기록이 없으면 userGame·review는 null이며 404가 아님", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-0: gameId가 숫자 형식이 아님", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun getMyDetailedReview(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable gameId: Long,
    ): RsData<DetailedReviewResponse> {
        val response = reviewService.getMyDetailedReview(securityUser.id, gameId)
        return RsData<DetailedReviewResponse>("200-8", "내 상세 리뷰 조회에 성공했습니다.", response)
    }

    @PutMapping("/user-games/{userGameId}/reviews")
    @Operation(
        summary = "리뷰 저장",
        description = """
            로그인한 사용자 본인의 게임 기록에 리뷰를 생성·수정합니다. 별점 또는 공백이 아닌 내용 중 하나 이상이 필요합니다.
            기존 활성 리뷰를 수정할 때는 조회 응답의 version이 필수입니다. 버전을 생략하거나 현재 값과 다르면 409-6입니다.
            새 리뷰·사용자가 삭제한 리뷰의 재작성 시에는 버전을 생략합니다. 재작성에서 버전을 보내면 현재 저장된 값과 일치해야 합니다.
            관리자 숨김 리뷰는 재작성할 수 없습니다(409-5). 성공 응답의 새 version을 이후 수정·삭제에 사용합니다.
            409-6이면 최신 리뷰를 다시 조회하고 변경 내용을 확인한 뒤 재시도합니다. 빈 별점·내용을 보내는 것은 삭제가 아니며, 삭제 전용 API를 사용해야 합니다.
        """,
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-6: 리뷰 저장 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 별점·버전 입력 검증 실패 / 400-2: 잘못된 JSON / 400-4: 별점·내용 모두 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-1: 다른 사용자의 게임 기록에 리뷰를 작성·변경할 수 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-2: 게임 기록을 찾을 수 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-5: 관리자 숨김 리뷰 재작성 불가 / 409-6: 기존 리뷰 버전 누락·불일치 또는 동시 수정 충돌", content = [Content(
            schema = Schema(implementation = RsData::class),
            examples = [ExampleObject(name = "리뷰 버전 충돌", value = REVIEW_VERSION_CONFLICT_EXAMPLE)],
        )]),
    ])
    fun saveReview(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable userGameId: Long,
        @Valid @RequestBody request: @Valid ReviewSaveRequest,
    ): RsData<ReviewResponse> {
        val response = gameRecordReviewService.saveReview(securityUser.id, userGameId, request)
        return RsData<ReviewResponse>("200-6", "리뷰가 저장되었습니다.", response)
    }

    @GetMapping("/reviews/{reviewId}")
    @Operation(
        summary = "리뷰 단건 조회",
        description = "로그인이 필요합니다. 리뷰 ID로 공개 중인 리뷰를 조회하며 삭제·관리자 숨김 리뷰는 404-3입니다. 수정·삭제 요청에는 응답의 version을 사용합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 공개 중인 리뷰 조회 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-0: reviewId가 숫자 형식이 아님", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-3: 리뷰 없음 또는 삭제·관리자 숨김 상태", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun getReview(@PathVariable reviewId: Long): RsData<ReviewResponse> {
        val response = reviewService.getReview(reviewId)
        return RsData<ReviewResponse>("200-1", "리뷰 조회에 성공했습니다.", response)
    }

    @GetMapping("/games/{gameId}/reviews")
    @Operation(summary = "게임 리뷰 목록 조회", description = "비로그인 조회가 가능합니다. 삭제·관리자 숨김 리뷰를 제외한 공개 리뷰를 페이지 단위로 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-2: 공개 리뷰 페이지 조회 성공. 일치하는 리뷰가 없으면 빈 목록이며 404가 아님", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-0: gameId가 숫자 형식이 아님", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun getGameReviews(
        @PathVariable gameId: Long,
        @PageableDefault(
            size = 20,
            sort = ["createdDate"],
            direction = Sort.Direction.DESC,
        )
        pageable: Pageable,
    ): RsData<ReviewPageResponse> {
        val response = reviewService.getGameReviews(gameId, pageable)
        return RsData<ReviewPageResponse>("200-2", "게임 리뷰 목록 조회에 성공했습니다.", response)
    }

    @GetMapping("/users/{userId}/reviews")
    @Operation(summary = "사용자 리뷰 목록 조회", description = "비로그인 조회가 가능합니다. 특정 사용자의 삭제·관리자 숨김 리뷰를 제외한 공개 리뷰를 페이지 단위로 조회합니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-3: 사용자 공개 리뷰 페이지 조회 성공. 일치하는 리뷰가 없으면 빈 목록이며 404가 아님", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-0: userId가 숫자 형식이 아님", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun getUserReviews(
        @PathVariable userId: Long,
        @PageableDefault(
            size = 20,
            sort = ["createdDate"],
            direction = Sort.Direction.DESC,
        )
        pageable: Pageable,
    ): RsData<ReviewPageResponse> {
        val response = reviewService.getUserReviews(userId, pageable)
        return RsData<ReviewPageResponse>("200-3", "사용자 리뷰 목록 조회에 성공했습니다.", response)
    }

    @PutMapping("/reviews/{reviewId}")
    @Operation(
        summary = "리뷰 수정",
        description = """
            로그인한 사용자 본인이 작성한 활성 리뷰의 별점·내용·스포일러 여부를 수정합니다.
            조회한 version을 요청 본문에 반드시 전달합니다. 누락·불일치 또는 동시 수정 충돌은 409-6입니다.
            성공 응답의 증가한 version을 다음 수정·삭제에 사용합니다.
            충돌 시 최신 내용을 다시 조회하고 사용자가 변경 내용을 확인한 뒤 재시도해야 합니다.
            삭제·관리자 숨김 리뷰는 이 API로 수정할 수 없습니다(404-3). 삭제 후 재작성은 리뷰 저장 API를 사용합니다.
        """,
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-4: 리뷰 수정 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 별점·버전 입력 검증 실패 / 400-2: 잘못된 JSON / 400-4: 별점·내용 모두 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-1: 본인의 리뷰만 수정 가능", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-3: 리뷰 없음 또는 삭제·관리자 숨김 상태", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-6: 버전 누락·불일치 또는 동시 수정 충돌. 최신 리뷰를 다시 조회해야 함", content = [Content(
            schema = Schema(implementation = RsData::class),
            examples = [ExampleObject(name = "리뷰 버전 충돌", value = REVIEW_VERSION_CONFLICT_EXAMPLE)],
        )]),
    ])
    fun updateReview(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable reviewId: Long,
        @Valid @RequestBody request: @Valid ReviewSaveRequest,
    ): RsData<ReviewResponse> {
        val response = gameRecordReviewService.updateReview(securityUser.id, reviewId, request)
        return RsData<ReviewResponse>("200-4", "리뷰가 수정되었습니다.", response)
    }

    @DeleteMapping("/reviews/{reviewId}")
    @Operation(
        summary = "리뷰 삭제",
        description = """
            로그인한 사용자 본인의 활성 리뷰를 소프트 삭제합니다.
            쿼리 파라미터 version에 조회 응답의 버전을 전달해야 합니다.
            바인딩상 생략할 수 있지만 실제로 생략하면 409-6을 반환하며 삭제하지 않습니다. 현재 버전과 불일치해도 동일합니다.
            충돌 시 최신 내용을 다시 조회하고 삭제 의사를 확인한 뒤 재시도합니다.
            리뷰 외에 다른 플레이 기록이 없으면 라이브러리에서 제외됩니다. 삭제한 리뷰의 재작성은 리뷰 저장 API를 사용합니다.
        """,
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-5: 리뷰 삭제 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-0: reviewId 또는 version이 숫자 형식이 아님", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-1: 본인의 리뷰만 삭제 가능", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-3: 리뷰 없음 또는 이미 삭제·관리자 숨김 상태", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-6: 버전 누락·불일치. 최신 리뷰를 다시 조회해야 함", content = [Content(
            schema = Schema(implementation = RsData::class),
            examples = [ExampleObject(name = "리뷰 버전 충돌", value = REVIEW_VERSION_CONFLICT_EXAMPLE)],
        )]),
    ])
    fun deleteReview(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @PathVariable reviewId: Long,
        @Parameter(
            description = "삭제하려는 리뷰의 조회 응답에서 받은 version. 필수이며 생략·불일치 시 409-6을 반환합니다.",
            example = "0",
            required = true,
        )
        @RequestParam(required = false)
        version: Long?,
    ): RsData<Void> {
        gameRecordReviewService.deleteReview(securityUser.id, reviewId, version)
        return RsData<Void>("200-5", "리뷰가 삭제되었습니다.")
    }
}

private const val REVIEW_VERSION_CONFLICT_EXAMPLE =
    """{"resultCode":"409-6","msg":"다른 곳에서 리뷰가 변경되었습니다. 최신 내용을 다시 불러온 후 시도해 주세요.","data":null}"""
