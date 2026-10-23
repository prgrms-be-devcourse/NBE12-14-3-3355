package com.gamelog.nbe121423355.domain.game.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "게임 검색 필터 목록")
@JvmRecord
data class GameFilterResponse(
    @field:Schema(description = "장르 선택지") val genres: List<Option>,
    @field:Schema(description = "플랫폼 선택지") val platforms: List<Option>,
) {
    @Schema(description = "필터 선택지")
    @JvmRecord
    data class Option(
        @field:Schema(description = "선택지 ID", example = "12") val id: Long?,
        @field:Schema(description = "표시 이름", example = "Role-playing (RPG)") val name: String?,
    ) {}
}
