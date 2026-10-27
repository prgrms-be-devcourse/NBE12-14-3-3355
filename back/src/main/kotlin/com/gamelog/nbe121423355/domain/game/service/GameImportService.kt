package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse.NamedResource
import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.game.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameImportService(
    private val gameRepository: GameRepository,
    private val genreRepository: GenreRepository,
    private val platformRepository: PlatformRepository,
    private val gameGenreRepository: GameGenreRepository,
    private val gamePlatformRepository: GamePlatformRepository,
    private val gameSeriesRepository: GameSeriesRepository,
    private val gameSeriesGameRepository: GameSeriesGameRepository,
    private val changeRecorder: RelatedGameChangeRecorder,
    private val recommendationRegistration: NewGameRecommendationRegistration,
) {
    @Transactional
    fun saveGames(responses: MutableList<IgdbGameResponse>): Int {
        val newGames = mutableListOf<Game>()
        val changedGames = mutableListOf<Game>()
        for (response in responses) {
            val existingGame = gameRepository.findByIgdbId(response.id).orElse(null)
            val game = existingGame?.apply { updateFromIgdb(response) }
                ?: gameRepository.save(Game.createFromIgdb(response)).also { newGames.add(it) }
            changedGames.add(game)
            saveGenres(game, response.genres)
            savePlatforms(game, response.platforms)
            saveSeries(game, response.collections)
        }
        gameRepository.flush()
        changedGames.sortedBy { it.id }.forEach { game ->
            val reason = if (game in newGames) RelatedGameChangeReason.GAME_ADDED
                else RelatedGameChangeReason.GAME_INFORMATION
            changeRecorder.record(requireNotNull(game.id), reason)
        }
        recommendationRegistration.register(newGames)
        return responses.size
    }

    private fun saveGenres(game: Game, genres: List<NamedResource>?) {
        for (response in genres.orEmpty()) {
            val genre =
                genreRepository
                    .findByIgdbGenreId(response.id)
                    .orElseGet {
                        genreRepository.save(Genre.createFromIgdb(response.id, response.name))
                    }
            val link = GameGenre(game, genre)
            if (!gameGenreRepository.existsById(requireNotNull(link.id))) {
                gameGenreRepository.save(link)
            }
        }
    }

    private fun savePlatforms(game: Game, platforms: List<NamedResource>?) {
        for (response in platforms.orEmpty()) {
            val platform =
                platformRepository
                    .findByIgdbPlatformsId(response.id)
                    .orElseGet {
                        platformRepository.save(
                            Platform.createFromIgdb(
                                response.id,
                                response.name,
                            )
                        )
                    }
            val link = GamePlatform(game, platform)
            if (!gamePlatformRepository.existsById(requireNotNull(link.id))) {
                gamePlatformRepository.save(link)
            }
        }
    }

    private fun saveSeries(game: Game, collections: List<NamedResource>?) {
        for (response in collections.orEmpty()) {
            val series =
                gameSeriesRepository
                    .findByIgdbId(response.id)
                    .orElseGet {
                        gameSeriesRepository.save(
                            GameSeries.createFromIgdb(
                                response.id,
                                response.name,
                            )
                        )
                    }
            // 기존 생성자의 인자 순서는 시리즈, 게임입니다.
            val link = GameSeriesGame(series, game)
            if (!gameSeriesGameRepository.existsById(requireNotNull(link.id))) {
                gameSeriesGameRepository.save(link)
            }
        }
    }
}
