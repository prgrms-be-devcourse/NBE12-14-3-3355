package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.*
import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.*
import com.gamelog.nbe121423355.domain.game.repository.*
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.interop.javaNonNull
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*
import java.util.stream.Collectors

@Service
@Transactional(readOnly = true)
class GameDetailService(
    private val gameRepository: GameRepository,
    private val publicationPolicy: GamePublicationPolicy,
    private val gameGenreRepository: GameGenreRepository,
    private val gamePlatformRepository: GamePlatformRepository,
    private val gameSeriesGameRepository: GameSeriesGameRepository,
    private val gameStatisticsRepository: GameStatisticsRepository,
    private val relatedGameQueryRepository: RelatedGameQueryRepository,
    private val relatedGameCandidateRepository: RelatedGameCandidateRepository,
    private val relatedGameCalculationRepository: RelatedGameCalculationRepository,
) {
    @Value("\${recommendation.related.precomputed-enabled:false}")
    private var relatedPrecomputedEnabled = false

    // 게임 기본 정보와 연결 정보, 상태·평점·리뷰·좋아요 통계를 조회해 상세 응답으로 반환
    fun getGameDetail(gameId: Long): GameDetailResponse {
        requirePublished(gameId)
        // GameLog DB ID로 게임 기본 정보 조회
        val game =
            gameRepository.findById(gameId)
                .orElseThrow { ServiceException("404-1", "존재하지 않는 게임입니다.") }
        // 게임에 연결된 장르 정보를 응답 DTO로 변환
        val genres =
            gameGenreRepository
                .findAllWithGenreByGameId(gameId)
                .stream()
                .map { gameGenre ->
                    GenreResponse(
                        id = gameGenre.genre.id,
                        name = gameGenre.genre.name,
                    )
                }
                .toList()
        // 게임에 연결된 플랫폼 정보를 응답 DTO로 변환
        val platforms =
            gamePlatformRepository
                .findAllWithPlatformByGameId(gameId)
                .stream()
                .map { gamePlatform ->
                    PlatformResponse(
                        id = gamePlatform.platform.id,
                        name = gamePlatform.platform.name,
                    )
                }
                .toList()
        // 게임에 연결된 시리즈 정보를 응답 DTO로 변환
        val series =
            gameSeriesGameRepository
                .findAllWithSeriesByGameId(gameId)
                .stream()
                .map { seriesGame ->
                    SeriesResponse(
                        id = seriesGame.series.id,
                        name = seriesGame.series.name
                    )
                }
                .toList()
        // 게임 ID를 기준으로 상태·평점·리뷰·좋아요 통계 조회
        val statistics = getStatistics(gameId)

        return GameDetailResponse(
            id = game.id,
            igdbId = game.igdbId,
            title = game.title,
            coverImageUrl = game.coverImageUrl,
            developer = game.developer,
            releaseDate = game.releaseDate,
            description = game.description,
            igdbRating = game.igdbRating,
            genres = genres,
            platforms = platforms,
            series = series,
            statistics = statistics,
        )
    }

    // 기준 게임의 존재 여부를 확인한 뒤 연관 추천 게임 상위 5개를 반환
    fun getRelatedGames(gameId: Long): List<RelatedGameResponse> {
        requirePublished(gameId)
        if (!gameRepository.existsById(gameId)) {
            throw ServiceException("404-1", "존재하지 않는 게임입니다.")
        }
        val games = findRelatedGames(gameId)
        if (games.isEmpty()) {
            return games
        }

        return attachGenres(games)
    }

    private fun requirePublished(gameId: Long) {
        if (
            publicationPolicy.isReadyOnly &&
            !relatedGameCalculationRepository.existsByGameIdAndLastSuccessAtIsNotNull(gameId)
        ) {
            throw ServiceException("404-1", "존재하지 않는 게임입니다.")
        }
    }

    private fun findRelatedGames(gameId: Long): List<RelatedGameResponse> {
        // 최초 계산 완료 여부로 판단하므로 재갱신 실패·진행 중에도 이전 정상 결과를 사용한다.
        return if (
            relatedPrecomputedEnabled &&
            relatedGameCalculationRepository.existsByGameIdAndLastSuccessAtIsNotNull(gameId)
        ) {
            relatedGameCandidateRepository.findStoredRelatedGames(
                gameId,
                PageRequest.of(0, 5),
            )
        } else {
            relatedGameQueryRepository.findRelatedGames(gameId)
        }
    }

    // 추천 게임들의 장르를 일괄 조회해 응답에 보강
    private fun attachGenres(
        games: List<RelatedGameResponse>
    ): List<RelatedGameResponse> {
        val gameIds = games.map { requireNotNull(it.id) }.toMutableList()

        val genresByGameId =
            gameGenreRepository.findAllWithGenreByGameIdIn(gameIds)
                .groupBy(
                    keySelector = { item ->
                        javaNonNull(
                            item.game.id,
                            "element cannot be mapped to a null key",
                        )
                    },
                    valueTransform = { item ->
                        GenreResponse(
                            id = item.genre.id,
                            name = item.genre.name,
                        )
                    },
                )

        return games.stream()
            .map { game ->
                game.copy(
                    genres = genresByGameId[game.id] ?: java.util.List.of()
                )
            }
            .toList()
    }

    private fun getStatistics(gameId: Long): GameStatisticsResponse {
        // 게임 ID를 기준으로 상태·평점·리뷰·좋아요 통계 조회
        val statusStatistics =
            gameStatisticsRepository.findStatusStatisticsByGameId(gameId)
        val ratingStatistics = getRatingStatistics(gameId)
        val likeCount = getLikeCount(gameId)
        val playTimeStatistics = getPlayTimeStatistics(gameId)

        // 조회한 통계를 게임 상세 응답 DTO로 변환
        return GameStatisticsResponse(
            playedCount = statusStatistics.playedCount,
            playingCount = statusStatistics.playingCount,
            backlogCount = statusStatistics.backlogCount,
            wishlistCount = statusStatistics.wishlistCount,
            likeCount = likeCount,
            averageRating = ratingStatistics.averageRating,
            ratingCount = ratingStatistics.ratingCount,
            reviewCount = ratingStatistics.reviewCount,
            ratingDistribution = ratingStatistics.ratingDistribution,
            averagePlayTimeHours = playTimeStatistics.averagePlayTimeHours,
            playTimeUserCount = playTimeStatistics.playTimeUserCount,
        )
    }

    // 평균 평점, 평가 수, 내용이 있는 리뷰 수와 모든 0.5점 단위의 평점 분포를 구성
    private fun getRatingStatistics(gameId: Long): GameRatingStatisticsResponse {
        val ratingStatistics =
            gameStatisticsRepository.findRatingStatisticsByGameId(gameId)

        val countsByRating: Map<Int, Long> =
            gameStatisticsRepository.findRatingDistributionByGameId(gameId)
                .stream()
                .collect(
                    Collectors.toMap(
                        { item ->
                            item.rating.multiply(BigDecimal.valueOf(2)).intValueExact()
                        },
                        { item -> item.count },
                    )
                )

        val distribution =
            Collections.unmodifiableList(
                (1..10).map { ratingStep ->
                    GameRatingDistributionResponse(
                        rating = BigDecimal.valueOf(ratingStep * 5L, 1),
                        count = countsByRating.getOrDefault(ratingStep, 0L),
                    )
                }
            )

        return GameRatingStatisticsResponse(
            averageRating = BigDecimal.valueOf(ratingStatistics.averageRating),
            ratingCount = ratingStatistics.ratingCount,
            reviewCount = ratingStatistics.reviewCount,
            ratingDistribution = distribution,
        )
    }

    // 게임에 좋아요를 누른 사용자 수 조회
    private fun getLikeCount(gameId: Long): Long {
        return gameStatisticsRepository.countLikesByGameId(gameId)
    }

    // 평균 플레이타임과 플레이타임 기록 사용자 수를 응답 DTO로 반환
    private fun getPlayTimeStatistics(gameId: Long): GamePlayTimeStatisticsResponse {
        val playTimeStatistics =
            gameStatisticsRepository.findPlayTimeStatisticsByGameId(gameId)

        return GamePlayTimeStatisticsResponse(
            averagePlayTimeHours = BigDecimal.valueOf(
                playTimeStatistics.averagePlayTimeHours
            ),
            playTimeUserCount = playTimeStatistics.playTimeUserCount,
        )
    }

}
