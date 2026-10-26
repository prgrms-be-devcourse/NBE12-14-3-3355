package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse.NamedResource
import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.game.repository.*
import java.util.ArrayList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameImportService
constructor(
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
        val newGames: MutableList<Game> = ArrayList()
        val changedGames = ArrayList<Game>()
        for (response in responses) {
            var game = gameRepository.findByIgdbId(response.id).orElse(null)
            if (game == null) {
                // 저장 후 ID가 부여된 객체를 사용합니다.
                game = gameRepository.save(Game.createFromIgdb(response))
                newGames.add(game)
            } else {
                game.updateFromIgdb(response)
            }
            changedGames.add(game)
            saveGenres(game, response.genres)
            savePlatforms(game, response.platforms)
            saveSeries(game, response.collections)
        }
        gameRepository.flush()
        changedGames.sortedBy { it.id }.forEach { game ->
            changeRecorder.record(requireNotNull(game.id),
                if (game in newGames) RelatedGameChangeReason.GAME_ADDED else RelatedGameChangeReason.GAME_INFORMATION)
        }
        recommendationRegistration.register(newGames)
        return responses.size
    }

    private fun saveGenres(game: Game, genres: MutableList<NamedResource>?) {
        if (genres == null) {
            return
        }
        for (response in genres) {
            val genre =
                genreRepository
                    .findByIgdbGenreId(response.id)
                    .orElseGet({
                        genreRepository.save(Genre.createFromIgdb(response.id, response.name))
                    })
            val link = GameGenre(game, genre)
            if (!gameGenreRepository.existsById(requireNotNull(link.id))) {
                gameGenreRepository.save(link)
            }
        }
    }

    private fun savePlatforms(game: Game, platforms: MutableList<NamedResource>?) {
        if (platforms == null) {
            return
        }
        for (response in platforms) {
            val platform: com.gamelog.nbe121423355.domain.game.entity.Platform =
                platformRepository
                    .findByIgdbPlatformsId(response.id)
                    .orElseGet({
                        platformRepository.save(
                            com.gamelog.nbe121423355.domain.game.entity.Platform.createFromIgdb(
                                response.id,
                                response.name,
                            )
                        )
                    })
            val link = GamePlatform(game, platform)
            if (!gamePlatformRepository.existsById(requireNotNull(link.id))) {
                gamePlatformRepository.save(link)
            }
        }
    }

    private fun saveSeries(game: Game, collections: MutableList<NamedResource>?) {
        if (collections == null) {
            return
        }
        for (response in collections) {
            val series =
                gameSeriesRepository
                    .findByIgdbId(response.id)
                    .orElseGet({
                        gameSeriesRepository.save(
                            GameSeries.createFromIgdb(
                                response.id,
                                response.name,
                            )
                        )
                    })
            // 기존 생성자의 인자 순서는 시리즈, 게임입니다.
            val link = GameSeriesGame(series, game)
            if (!gameSeriesGameRepository.existsById(requireNotNull(link.id))) {
                gameSeriesGameRepository.save(link)
            }
        }
    }
}
