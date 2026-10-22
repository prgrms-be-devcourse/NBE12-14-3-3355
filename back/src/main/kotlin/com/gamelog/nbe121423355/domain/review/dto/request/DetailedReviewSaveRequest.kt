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
    @field:Schema(description = "저장할 리뷰. 게임 기록만 저장할 때는 null입니다.", nullable = true)
    val review: ReviewSaveRequest?,
) {}
