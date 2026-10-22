package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.*
import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.GenreResponse
import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.PlatformResponse
import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.SeriesResponse
import com.gamelog.nbe121423355.domain.game.repository.*
import com.gamelog.nbe121423355.domain.game.repository.projection.GamePlayTimeStatisticsProjection
import com.gamelog.nbe121423355.domain.game.repository.projection.GameRatingDistributionProjection
import com.gamelog.nbe121423355.domain.game.repository.projection.GameRatingStatisticsProjection
import com.gamelog.nbe121423355.domain.game.repository.projection.GameStatusStatisticsProjection
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.math.BigDecimal
import java.util.stream.Collectors
import java.util.stream.IntStream
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GameDetailService
constructor(
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
            gameRepository
                .findById(gameId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 게임입니다.") })
        // 게임에 연결된 장르 정보를 응답 DTO로 변환
        val genres =
            gameGenreRepository
                .findAllWithGenreByGameId(gameId)
                .stream()
                .map({ gameGenre ->
                    GenreResponse(
                        gameGenre.genre.id,
                        gameGenre.genre.name,
                    )
                })
                .toList()
        // 게임에 연결된 플랫폼 정보를 응답 DTO로 변환
        val platforms =
            gamePlatformRepository
                .findAllWithPlatformByGameId(gameId)
                .stream()
                .map({ gamePlatform ->
                    PlatformResponse(
                        gamePlatform.platform.id,
                        gamePlatform.platform.name,
                    )
                })
                .toList()
        // 게임에 연결된 시리즈 정보를 응답 DTO로 변환
        val series =
            gameSeriesGameRepository
                .findAllWithSeriesByGameId(gameId)
                .stream()
                .map({ seriesGame -> SeriesResponse(seriesGame.series.id, seriesGame.series.name) })
                .toList()
        // 게임 ID를 기준으로 상태·평점·리뷰·좋아요 통계 조회
        val statusStatistics: GameStatusStatisticsProjection =
            gameStatisticsRepository.findStatusStatisticsByGameId(gameId)
        val ratingStatistics = getRatingStatistics(gameId)
        val likeCount = getLikeCount(gameId)
        val playTimeStatistics = getPlayTimeStatistics(gameId)
        // 조회한 통계를 게임 상세 응답 DTO로 변환
        val statistics =
            GameStatisticsResponse(
                statusStatistics.playedCount,
                statusStatistics.playingCount,
                statusStatistics.backlogCount,
                statusStatistics.wishlistCount,
                likeCount,
                ratingStatistics.averageRating,
                ratingStatistics.ratingCount,
                ratingStatistics.reviewCount,
                ratingStatistics.ratingDistribution,
                playTimeStatistics.averagePlayTimeHours,
                playTimeStatistics.playTimeUserCount,
            )
        return GameDetailResponse(
            game.id,
            game.igdbId,
            game.title,
            game.coverImageUrl,
            game.developer,
            game.releaseDate,
            game.description,
            game.igdbRating,
            genres,
            platforms,
            series,
            statistics,
        )
    }

    // 기준 게임의 존재 여부를 확인한 뒤 연관 추천 게임 상위 5개를 반환
    fun getRelatedGames(gameId: Long): MutableList<RelatedGameResponse> {
        requirePublished(gameId)
        if (!gameRepository.existsById(gameId)) {
            throw ServiceException("404-1", "존재하지 않는 게임입니다.")
        }
        // 최초 계산 완료 여부로 판단하므로 재갱신 실패·진행 중에도 이전 정상 결과를 사용한다.
        val games: MutableList<RelatedGameResponse> =
            if (
                relatedPrecomputedEnabled &&
                    relatedGameCalculationRepository.existsByGameIdAndLastSuccessAtIsNotNull(gameId)
            )
                relatedGameCandidateRepository.findStoredRelatedGames(
                    gameId,
                    PageRequest.of(0, 5),
                )
            else relatedGameQueryRepository.findRelatedGames(gameId)
        if (games.isEmpty()) {
            return games
        }
        val genresByGameId: MutableMap<Long, MutableList<GenreResponse>> =
            gameGenreRepository
                .findAllWithGenreByGameIdIn(games.map { requireNotNull(it.id) }.toMutableList())
                .stream()
                .collect(
                    Collectors.groupingBy(
                        { item -> item.game.id },
                        Collectors.mapping(
                            { item ->
                                GenreResponse(
                                    item.genre.id,
                                    item.genre.name,
                                )
                            },
                            Collectors.toList(),
                        ),
                    )
                )
        return games
            .stream()
            .map({ item ->
                RelatedGameResponse(
                    item.id,
                    item.title,
                    item.coverImageUrl,
                    item.igdbRating,
                    item.recommendationScore,
                    genresByGameId.getOrDefault(item.id, java.util.List.of()),
                )
            })
            .toList()
    }

    private fun requirePublished(gameId: Long?) {
        if (
            publicationPolicy.isReadyOnly &&
                !relatedGameCalculationRepository.existsByGameIdAndLastSuccessAtIsNotNull(gameId)
        ) {
            throw ServiceException("404-1", "존재하지 않는 게임입니다.")
        }
    }

    // 평균 평점, 평가 수, 내용이 있는 리뷰 수와 모든 0.5점 단위의 평점 분포를 구성
    private fun getRatingStatistics(gameId: Long?): GameRatingStatisticsResponse {
        val ratingStatistics: GameRatingStatisticsProjection =
            gameStatisticsRepository.findRatingStatisticsByGameId(gameId)
        val countsByRating: MutableMap<Int, Long> =
            gameStatisticsRepository
                .findRatingDistributionByGameId(gameId)
                .stream()
                .collect(
                    Collectors.toMap(
                        { item -> item.rating.multiply(BigDecimal.valueOf(2)).intValueExact() },
                        { obj: GameRatingDistributionProjection -> obj.count },
                    )
                )
        val distribution =
            IntStream.rangeClosed(1, 10)
                .mapToObj({ ratingStep ->
                    GameRatingDistributionResponse(
                        BigDecimal.valueOf(ratingStep * 5L, 1),
                        countsByRating.getOrDefault(ratingStep, 0L),
                    )
                })
                .toList()
        return GameRatingStatisticsResponse(
            BigDecimal.valueOf(ratingStatistics.averageRating),
            ratingStatistics.ratingCount,
            ratingStatistics.reviewCount,
            distribution,
        )
    }

    // 게임에 좋아요를 누른 사용자 수 조회
    private fun getLikeCount(gameId: Long?): Long {
        return gameStatisticsRepository.countLikesByGameId(gameId)
    }

    // 평균 플레이타임과 플레이타임 기록 사용자 수를 응답 DTO로 반환
    private fun getPlayTimeStatistics(gameId: Long?): GamePlayTimeStatisticsResponse {
        val playTimeStatistics: GamePlayTimeStatisticsProjection =
            gameStatisticsRepository.findPlayTimeStatisticsByGameId(gameId)
        return GamePlayTimeStatisticsResponse(
            BigDecimal.valueOf(playTimeStatistics.averagePlayTimeHours),
            playTimeStatistics.playTimeUserCount,
        )
    }
}
