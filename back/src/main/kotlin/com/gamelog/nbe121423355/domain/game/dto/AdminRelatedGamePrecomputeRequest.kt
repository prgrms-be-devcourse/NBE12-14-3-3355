package com.gamelog.nbe121423355.domain.game.dto

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// gameIds는 관리자 입력창의 쉼표·공백·줄바꿈 구분 문자열을 받는다.
data class AdminRelatedGamePrecomputeRequest(
    @field:NotNull
    @field:Schema(description = "실행 대상 종류. ALL·IDS·FAILED·UNREADY·CHANGED 중 선택합니다. IDS일 때만 gameIds를 입력합니다.", example = "IDS")
    val type: RelatedGamePrecomputeTargetType?,
    @field:Size(max = 30_000)
    @field:Schema(description = "IDS 전용 GameLog DB 게임 ID 문자열. 쉼표·공백·줄바꿈으로 구분하며, 중복 제거 후 최대 1,000개 양수 ID만 허용합니다. 다른 type에서는 생략합니다.", example = "1, 2, 3", maxLength = 30000)
    val gameIds: String? = null,
    @field:Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    @field:Schema(description = "재실행할 종료된 원본 runId(UUID). 생략하면 새 실행입니다. 원본과 type·게임 ID·initializeBaseline이 같아야 합니다.", nullable = true)
    val sourceRunId: String? = null,
    @field:Schema(description = "초기 기준선 등록 여부. true는 ALL 실행에서만 허용합니다.", defaultValue = "false")
    val initializeBaseline: Boolean = false,
)
