package com.gamelog.nbe121423355.domain.review.dto.request

import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

@JvmRecord
data class DetailedReviewSaveRequest(
    @field:NotNull(message = "게임 기록은 필수입니다.")
    @Valid
    @field:Schema(description = "저장할 게임 기록", requiredMode = Schema.RequiredMode.REQUIRED)
    val userGame: UserGameReqBody?,
    @Valid
    @field:Schema(description = "함께 저장할 리뷰. null이거나 별점·내용이 모두 비어 있으면 기존 리뷰를 유지하며 삭제하지 않습니다. 기존 활성 리뷰를 변경할 때는 review.version이 필요하고, 충돌 시 게임 기록 변경도 롤백됩니다. 삭제는 리뷰 삭제 API를 사용합니다.", nullable = true)
    val review: ReviewSaveRequest?,
) {}
