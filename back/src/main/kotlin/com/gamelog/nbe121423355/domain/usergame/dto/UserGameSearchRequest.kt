package com.gamelog.nbe121423355.domain.usergame.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

class UserGameSearchRequest {

    @Schema(description = "라이브러리 상태 필터", example = "ALL", defaultValue = "ALL")
    @field:NotNull
    var status: UserGameTab? = UserGameTab.ALL

    @Schema(description = "정렬 기준", example = "RECENT_PLAYED", defaultValue = "RECENT_PLAYED")
    @field:NotNull
    var sort: UserGameSort? = UserGameSort.RECENT_PLAYED

    @Schema(description = "게임 제목 검색어", example = "Baldur", nullable = true)
    @field:Size(max = 255)
    var keyword: String? = null

    @Schema(description = "플랫폼 ID 필터", example = "[1, 6]", nullable = true)
    @field:Size(max = 100)
    var platformIds: MutableList<@NotNull @Positive Long>? = null

    @Schema(description = "장르 ID 필터", example = "[5, 12]", nullable = true)
    @field:Size(max = 100)
    var genreIds: MutableList<@NotNull @Positive Long>? = null

    @Schema(description = "페이지 번호(0부터 시작)", example = "0", defaultValue = "0", minimum = "0")
    @field:Min(0)
    var page: Int = 0

    @Schema(
        description = "페이지 크기",
        example = "20",
        defaultValue = "60",
        minimum = "1",
        maximum = "100",
    )
    @field:Min(1)
    @field:Max(100)
    var size: Int = 60
}
