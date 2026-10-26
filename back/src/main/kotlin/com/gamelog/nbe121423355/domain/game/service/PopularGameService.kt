package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.PopularGameResponse
import com.gamelog.nbe121423355.domain.game.dto.PopularGameResponse.GenreResponse
import com.gamelog.nbe121423355.domain.game.repository.GameGenreRepository
import com.gamelog.nbe121423355.domain.game.repository.GameStatisticsRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PopularGameService(
    private val gameStatisticsRepository: GameStatisticsRepository,
    private val gameGenreRepository: GameGenreRepository,
) {
    // 좋아요 순으로 조회한 게임에 장르 정보를 일괄 조회해 함께 반환한다.
    fun getPopularGames(size: Int): List<PopularGameResponse> {
        validateSize(size)
        val popularGames = gameStatisticsRepository.findPopularGames(PageRequest.of(0, size))
        // 인기 게임이 없으면 장르 조회를 생략한다.
        if (popularGames.isEmpty()) {
            return emptyList()
        }
        val gameIds = popularGames.map { it.gameId }
        // 게임별 장르를 묶으면서 조회 쿼리가 정한 장르명 순서를 유지한다.
        val genresByGameId =
            gameGenreRepository
                .findAllWithGenreByGameIdIn(gameIds)
                .groupBy(
                    keySelector = { gameGenre ->
                        requireNotNull(gameGenre.game.id) { "조회한 게임의 ID가 없습니다." }
                    },
                    valueTransform = { gameGenre ->
                        GenreResponse(
                            id = requireNotNull(gameGenre.genre.id) { "조회한 장르의 ID가 없습니다." },
                            name = requireNotNull(gameGenre.genre.name) { "조회한 장르의 이름이 없습니다." },
                        )
                    },
                )

        val responses = popularGames.map { game ->
            PopularGameResponse(
                gameId = game.gameId,
                title = game.title,
                coverImageUrl = game.coverImageUrl,
                likeCount = game.likeCount,
                genres = genresByGameId[game.gameId].orEmpty(),
            )
        }
        // 조회 순서를 유지하고 반환 목록의 추가·삭제를 허용하지 않는다.
        return java.util.List.copyOf(responses)
    }

    private fun validateSize(size: Int) {
        if (size !in 1..MAX_SIZE) {
            throw ServiceException("400-9", "조회 개수는 1 이상 50 이하여야 합니다.")
        }
    }

    companion object {
        private const val MAX_SIZE = 50
    }
}
