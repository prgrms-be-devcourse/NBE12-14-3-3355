package com.gamelog.nbe121423355.domain.game.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Order

@Schema(description = "게임 목록 검색 조건")
class GameSearchRequest(
    @Schema(
        description = "페이지 번호(0부터 시작)",
        example = "0",
        defaultValue = "0",
        minimum = "0",
    )
    page: Int?,
    @Schema(
        description = "페이지 크기",
        example = "20",
        defaultValue = "20",
        minimum = "1",
        maximum = "100",
    )
    size: Int?,
    @Schema(
        description = "정렬 기준 (미지정 시 IGDB 평점 내림차순)",
        example = "LATEST",
        allowableValues = ["LATEST", "TITLE", "RATING", "LIBRARY", "PLAY_TIME"],
    )
    val sort: GameSort?,
    @Schema(description = "게임 제목 검색어", example = "Baldur", nullable = true) val keyword: String?,
    @Schema(
        description = "장르 ID 목록",
        example = "[12, 31]",
        nullable = true,
    )
    val genreIds: List<Long>?,
    @Schema(
        description = "플랫폼 ID 목록",
        example = "[6, 48]",
        nullable = true,
    )
    val platformIds: List<Long>?,
) {
    fun toPageable(): Pageable {
        // 기본 IGDB 평점과 집계 정렬은 Repository에서 처리하며 동점은 ID순입니다.
        val order =
            if (sort == null) Sort.by("id").ascending()
            else
                when (sort) {
                    GameSort.LATEST -> Sort.by(Sort.Order.desc("releaseDate"), Sort.Order.asc("id"))
                    GameSort.TITLE -> Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"))
                    GameSort.RATING,
                    GameSort.LIBRARY,
                    GameSort.PLAY_TIME -> Sort.by("id").ascending()
                }
        return PageRequest.of(
            page,
            size,
            order,
        )
    }

    @field:Min(0) val page: Int = page ?: 0
    @field:Min(1) @field:Max(100) val size: Int = size ?: 20

    override fun equals(other: Any?): Boolean =
        other is GameSearchRequest &&
            this.page == other.page &&
            this.size == other.size &&
            this.sort == other.sort &&
            this.keyword == other.keyword &&
            this.genreIds == other.genreIds &&
            this.platformIds == other.platformIds

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + page.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + (sort?.hashCode() ?: 0)
        result = 31 * result + (keyword?.hashCode() ?: 0)
        result = 31 * result + (genreIds?.hashCode() ?: 0)
        result = 31 * result + (platformIds?.hashCode() ?: 0)
        return result
    }
}
