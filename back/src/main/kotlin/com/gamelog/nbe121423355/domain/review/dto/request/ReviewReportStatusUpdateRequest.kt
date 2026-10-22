package com.gamelog.nbe121423355.domain.review.dto.request

import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@JvmRecord
data class ReviewReportStatusUpdateRequest(
    @field:NotNull(message = "신고 처리 상태는 필수입니다.")
    @field:Schema(description = "변경할 신고 처리 상태", example = "APPROVED")
    val status: ReportStatus?
) {}
