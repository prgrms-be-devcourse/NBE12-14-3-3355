package com.gamelog.nbe121423355.domain.usergame.dto

import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class UserGameReqBody(
    @field:Schema(description = "플레이 완료 세부 상태", example = "COMPLETED", nullable = true)
    val playStatus: PlayStatus?,
    @field:Schema(description = "현재 플레이 중 여부", example = "false") val isPlaying: Boolean,
    @field:Schema(description = "플레이 예정 여부", example = "false") val isBacklog: Boolean,
    @field:Schema(description = "위시리스트 여부", example = "false") val isWishlist: Boolean,
    @field:Schema(description = "좋아하는 게임 여부", example = "true") val isLiked: Boolean,
    @field:Schema(description = "선택한 플랫폼 ID", example = "1", nullable = true) val platformId: Long?,
    @field:PositiveOrZero
    @field:Schema(description = "실제 플레이 시간", example = "42.5", minimum = "0", nullable = true)
    val playTimeHours: BigDecimal?,
    @field:PositiveOrZero
    @field:Schema(description = "메인 목표 완료 시간", example = "35.0", minimum = "0", nullable = true)
    val finishTimeHours: BigDecimal?,
    @field:PositiveOrZero
    @field:Schema(description = "완전 공략 시간", example = "70.0", minimum = "0", nullable = true)
    val masterTimeHours: BigDecimal?,
    @field:Schema(
        description = "플레이 시작일. 오늘보다 미래일 수 없습니다.",
        example = "2026-09-01",
        nullable = true,
    )
    val startedAt: LocalDate?,
    @field:Schema(
        description = "플레이 완료일. 시작일과 같거나 이후이며 오늘보다 미래일 수 없습니다.",
        example = "2026-09-20",
        nullable = true,
    )
    val completedAt: LocalDate?,
    @field:Schema(
        description = "마지막 플레이 일시. 시작일과 같거나 이후이며 오늘보다 미래 날짜는 입력할 수 없습니다.",
        example = "2026-09-20T00:00:00",
        nullable = true,
    )
    val lastPlayedAt: LocalDateTime?,
) {}
