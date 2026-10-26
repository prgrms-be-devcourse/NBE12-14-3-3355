package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.*
import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.*
import com.gamelog.nbe121423355.domain.game.repository.*
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

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
        val game = gameRepository.findByIdOrNull(gameId)
            ?: throw ServiceException("404-1", "존재하지 않는 게임입니다.")
        // 게임에 연결된 장르 정보를 응답 DTO로 변환
        val genres = gameGenreRepository.findAllWithGenreByGameId(gameId).map { gameGenre ->
            GenreResponse(
                id = requireNotNull(gameGenre.genre.id) { "조회한 장르의 ID가 없습니다." },
                name = requireNotNull(gameGenre.genre.name) { "조회한 장르의 이름이 없습니다." },
            )
        }
        // 게임에 연결된 플랫폼 정보를 응답 DTO로 변환
        val platforms = gamePlatformRepository.findAllWithPlatformByGameId(gameId).map { gamePlatform ->
            PlatformResponse(
                id = requireNotNull(gamePlatform.platform.id) { "조회한 플랫폼의 ID가 없습니다." },
                name = requireNotNull(gamePlatform.platform.name) { "조회한 플랫폼의 이름이 없습니다." },
            )
        }
        // 게임에 연결된 시리즈 정보를 응답 DTO로 변환
        val series = gameSeriesGameRepository.findAllWithSeriesByGameId(gameId).map { seriesGame ->
            SeriesResponse(
                id = requireNotNull(seriesGame.series.id) { "조회한 시리즈의 ID가 없습니다." },
                name = requireNotNull(seriesGame.series.name) { "조회한 시리즈의 이름이 없습니다." },
            )
        }
        // 게임 ID를 기준으로 상태·평점·리뷰·좋아요 통계 조회
        val statistics = getStatistics(gameId)

        return GameDetailResponse(
            id = requireNotNull(game.id) { "조회한 게임의 ID가 없습니다." },
            igdbId = game.igdbId,
            title = requireNotNull(game.title) { "조회한 게임의 제목이 없습니다." },
            coverImageUrl = game.coverImageUrl,
            developer = game.developer,
            releaseDate = game.releaseDate,
            description = game.description,
            igdbRating = game.igdbRating,
            genres = java.util.List.copyOf(genres),
            platforms = java.util.List.copyOf(platforms),
            series = java.util.List.copyOf(series),
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
        val gameIds = games.map { it.id }

        val genresByGameId =
            gameGenreRepository.findAllWithGenreByGameIdIn(gameIds)
                .groupBy(
                    keySelector = { item ->
                        requireNotNull(item.game.id) { "조회한 게임의 ID가 없습니다." }
                    },
                    valueTransform = { item ->
                        GenreResponse(
                            id = requireNotNull(item.genre.id) { "조회한 장르의 ID가 없습니다." },
                            name = requireNotNull(item.genre.name) { "조회한 장르의 이름이 없습니다." },
                        )
                    },
                )

        val responses = games.map { game ->
            game.copy(genres = genresByGameId[game.id].orEmpty())
        }
        return java.util.List.copyOf(responses)
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

        // SQL이 별점별로 집계하므로 0.5점 단위 구간은 중복되지 않는다.
        val countsByRating = gameStatisticsRepository.findRatingDistributionByGameId(gameId)
            .associate { item ->
                item.rating.multiply(BigDecimal.valueOf(2)).intValueExact() to item.count
            }
        val distribution = (1..10).map { ratingStep ->
            GameRatingDistributionResponse(
                rating = BigDecimal.valueOf(ratingStep * 5L, 1),
                count = countsByRating.getOrDefault(ratingStep, 0L),
            )
        }

        return GameRatingStatisticsResponse(
            averageRating = BigDecimal.valueOf(ratingStatistics.averageRating),
            ratingCount = ratingStatistics.ratingCount,
            reviewCount = ratingStatistics.reviewCount,
            ratingDistribution = java.util.List.copyOf(distribution),
        )
    }

    // 게임에 좋아요를 누른 사용자 수 조회
    private fun getLikeCount(gameId: Long): Long = gameStatisticsRepository.countLikesByGameId(gameId)

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
