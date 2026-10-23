package com.gamelog.nbe121423355.domain.game.dto

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// gameIds는 관리자 입력창의 쉼표·공백·줄바꿈 구분 문자열을 받는다.
data class AdminRelatedGamePrecomputeRequest(
    @field:NotNull
    val type: RelatedGamePrecomputeTargetType?,
    @field:Size(max = 30_000)
    val gameIds: String? = null,
    @field:Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    val sourceRunId: String? = null,
)
