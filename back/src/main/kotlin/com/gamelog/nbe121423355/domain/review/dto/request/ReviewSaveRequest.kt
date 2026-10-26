package com.gamelog.nbe121423355.domain.review.dto.request

import com.gamelog.nbe121423355.domain.review.validation.ValidRating
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal

@JvmRecord
data class ReviewSaveRequest(
    @field:PositiveOrZero(message = "리뷰 버전은 0 이상이어야 합니다.")
    @field:Schema(
        description = "기존 활성 리뷰 수정 시 조회 응답의 version이 필수입니다. 누락·불일치 시 409-6입니다. 새 리뷰·사용자 삭제 후 재작성 시에는 생략하며, 재작성에 값을 보내면 현재 저장된 버전과 일치해야 합니다. 성공 응답의 새 버전을 다음 변경에 사용합니다.",
        example = "0",
        nullable = true,
    )
    val version: Long?,
    @ValidRating
    @field:DecimalMin(value = "0.5", message = "별점은 0.5 이상이어야 합니다.")
    @field:DecimalMax(
        value = "5.0",
        message = "별점은 5.0 이하여야 합니다.",
    )
    @field:Schema(description = "선택적 별점. 0.5~5.0 범위의 0.5점 단위입니다. 리뷰 전용 저장·수정에서는 별점 또는 공백이 아닌 내용 중 하나 이상이 필요합니다.", example = "4.5", nullable = true)
    val rating: BigDecimal?,
    @field:Schema(
        description = "리뷰 내용. 별점만 저장하는 경우 생략할 수 있습니다.",
        example = "스토리와 전투가 재미있어요.",
        nullable = true,
    )
    val content: String?,
    @field:Schema(description = "스포일러 포함 여부", example = "false") val spoiler: Boolean,
) {
    constructor(
        rating: BigDecimal?,
        content: String?,
        spoiler: Boolean,
    ) : this(null, rating, content, spoiler)

    fun hasReviewContent(): Boolean {
        return rating != null || (content != null && !content.javaIsBlank())
    }
}
