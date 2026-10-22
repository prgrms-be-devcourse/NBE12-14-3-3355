package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.PopularGameResponse
import com.gamelog.nbe121423355.domain.game.dto.PopularGameResponse.GenreResponse
import com.gamelog.nbe121423355.domain.game.repository.GameGenreRepository
import com.gamelog.nbe121423355.domain.game.repository.GameStatisticsRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.PopularGameProjection
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.util.stream.Collectors
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PopularGameService
constructor(
    private val gameStatisticsRepository: GameStatisticsRepository,
    private val gameGenreRepository: GameGenreRepository,
) {
    fun getPopularGames(size: Int): MutableList<PopularGameResponse> {
        validateSize(size)
        val popularGames: MutableList<PopularGameProjection> =
            gameStatisticsRepository.findPopularGames(PageRequest.of(0, size))
        if (popularGames.isEmpty()) {
            return java.util.List.of()
        }
        val gameIds: MutableList<Long>? =
            popularGames
                .stream()
                .map({ obj: PopularGameProjection -> requireNotNull(obj.gameId) })
                .toList()
        val genresByGameId: MutableMap<Long, MutableList<GenreResponse>> =
            gameGenreRepository
                .findAllWithGenreByGameIdIn(gameIds)
                .stream()
                .collect(
                    Collectors.groupingBy(
                        { gameGenre -> gameGenre.game.id },
                        Collectors.mapping(
                            { gameGenre ->
                                GenreResponse(
                                    gameGenre.genre.id,
                                    gameGenre.genre.name,
                                )
                            },
                            Collectors.toList(),
                        ),
                    )
                )
        return popularGames
            .stream()
            .map({ game ->
                PopularGameResponse(
                    game.gameId,
                    game.title,
                    game.coverImageUrl,
                    game.likeCount,
                    genresByGameId.getOrDefault(game.gameId, java.util.List.of()),
                )
            })
            .toList()
    }

    private fun validateSize(size: Int) {
        if (size < 1 || size > PopularGameService.MAX_SIZE) {
            throw ServiceException("400-9", "조회 개수는 1 이상 50 이하여야 합니다.")
        }
    }

    companion object {
        private const val MAX_SIZE = 50
    }
}
