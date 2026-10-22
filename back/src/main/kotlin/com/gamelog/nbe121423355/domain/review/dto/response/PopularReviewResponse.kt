package com.gamelog.nbe121423355.domain.review.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "인기 리뷰 정보")
@JvmRecord
data class PopularReviewResponse(
    @field:Schema(description = "리뷰 ID", example = "13") val reviewId: Long?,
    @field:Schema(description = "작성자 ID", example = "4") val userId: Long?,
    @field:Schema(description = "작성자 닉네임", example = "게임마스터") val nickname: String?,
    @field:Schema(description = "작성자 프로필 이미지 URL", nullable = true) val profileImageUrl: String?,
    @field:Schema(description = "게임 ID", example = "6") val gameId: Long?,
    @field:Schema(description = "게임 제목", example = "Baldur's Gate II") val gameTitle: String?,
    @field:Schema(description = "게임 커버 이미지 URL", nullable = true) val gameCoverImageUrl: String?,
    @field:Schema(description = "별점", example = "4.5", nullable = true) val rating: BigDecimal?,
    @field:Schema(description = "리뷰 내용", example = "정말 재미있게 플레이했습니다.") val content: String?,
    @field:Schema(description = "스포일러 포함 여부", example = "false") val spoiler: Boolean,
    @field:Schema(description = "좋아요 수", example = "12") val likeCount: Long,
    @field:Schema(description = "작성 일시") val createdDate: LocalDateTime?,
) {}
