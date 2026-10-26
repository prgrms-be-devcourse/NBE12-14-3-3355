package com.gamelog.nbe121423355.domain.review.dto.request

import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@JvmRecord
data class ReviewReportStatusUpdateRequest(
    @field:NotNull(message = "신고 처리 상태는 필수입니다.")
    @field:Schema(description = "처리 결과는 APPROVED(승인) 또는 REJECTED(반려)만 가능합니다. PENDING은 400-5, 누락은 400-1입니다. 같은 리뷰에 접수된 모든 대기 신고에 적용됩니다.", example = "APPROVED")
    val status: ReportStatus?
) {}
