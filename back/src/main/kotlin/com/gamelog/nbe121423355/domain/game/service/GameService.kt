package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameListResponse
import com.gamelog.nbe121423355.domain.game.dto.GameSearchRequest
import com.gamelog.nbe121423355.domain.game.repository.GameFilteredQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.global.interop.javaIsBlank
import com.gamelog.nbe121423355.global.interop.strip
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameService
constructor(
    private val gameRepository: GameRepository,
    private val gameFilteredQueryRepository: GameFilteredQueryRepository,
) {
    @Transactional(readOnly = true)
    fun getGamesPage(request: GameSearchRequest): Page<GameListResponse> {
        val keyword = request.keyword
        val genreIds = request.genreIds
        val platformIds = request.platformIds
        val keywordPattern: String? =
            if (keyword == null || keyword.javaIsBlank()) null
            else toContainsPattern(keyword.strip())
        val filterGenres = genreIds != null && !genreIds.isEmpty()
        val filterPlatforms = platformIds != null && !platformIds.isEmpty()
        // 전체 목록을 포함해 필터가 없는 기본 정렬은 ID를 먼저 조회합니다.
        val useDefaultSearch = !filterGenres && !filterPlatforms && request.sort == null
        if (useDefaultSearch) {
            // 전체 목록의 기존 응답에 포함되던 pageable.sort 메타데이터도 유지합니다.
            val pageable: org.springframework.data.domain.Pageable =
                if (keywordPattern == null) request.toPageable()
                else PageRequest.of(request.page, request.size)
            val minDate = GameService.MIN_RELEASE_DATE
            val maxDate = LocalDate.now(GameService.CATALOG_ZONE).plusDays(1)
            val content =
                gameRepository.findDefaultSearchContent(
                    keywordPattern,
                    minDate,
                    maxDate,
                    pageable.getPageSize(),
                    pageable.offset,
                )
            // 마지막 부분 페이지는 크기로 총 개수를 알 수 있습니다.
            // 범위를 벗어난 빈 페이지는 COUNT를 실행하여 실제 총 개수를 유지합니다.
            return PageableExecutionUtils.getPage(
                    content,
                    pageable,
                    java.util.function.LongSupplier {
                        gameRepository.countDefaultSearch(keywordPattern, minDate, maxDate)
                    },
                )
                .map<GameListResponse>(
                    java.util.function.Function { value -> GameListResponse(value) }
                )
        }
        return gameFilteredQueryRepository
            .findPage(
                keywordPattern,
                genreIds,
                platformIds,
                request.sort,
                GameService.MIN_RELEASE_DATE,
                LocalDate.now(GameService.CATALOG_ZONE).plusDays(1),
                request.toPageable(),
            )
            .map<GameListResponse>(java.util.function.Function { value -> GameListResponse(value) })
    }

    @Transactional(readOnly = true)
    fun getSuggestions(keyword: String?): MutableList<GameListResponse> {
        if (keyword == null || keyword.javaIsBlank()) return java.util.List.of()
        val normalized = keyword.strip()
        val escaped = escapeLike(normalized)
        return gameRepository
            .findSuggestions(
                normalized,
                escaped.toString() + "%",
                "%" + escaped + "%",
                GameService.MIN_RELEASE_DATE,
                LocalDate.now(GameService.CATALOG_ZONE).plusDays(1),
            )
            .stream()
            .map({ value -> GameListResponse(value) })
            .toList()
    }

    private fun escapeLike(keyword: String): String {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_")
    }

    private fun toContainsPattern(keyword: String): String {
        return "%" + escapeLike(keyword) + "%"
    }

    companion object {
        private val MIN_RELEASE_DATE: LocalDate? = LocalDate.of(1970, 1, 1)
        private val CATALOG_ZONE: ZoneId? = ZoneId.of("Asia/Seoul")
    }
}
