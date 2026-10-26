package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.GenreResponse
import com.gamelog.nbe121423355.domain.game.dto.PersonalizedGameRecommendationResponse
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedGameRecordQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedOnboardingPreferenceQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRecommendationCandidateQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedGameRecordProjection
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRecommendationCandidateProjection
import com.gamelog.nbe121423355.domain.game.service.model.PersonalizedGenrePreference
import com.gamelog.nbe121423355.domain.game.service.model.PersonalizedRecommendationGenre
import com.gamelog.nbe121423355.domain.game.service.model.PersonalizedRecommendationResult
import java.math.BigDecimal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 게임 기록을 우선하고 온보딩을 후순위로 사용해 맞춤 추천 결과를 생성
@Service
@Transactional(readOnly = true)
class PersonalizedGameRecommendationService(
    private val gameRecordQueryRepository: PersonalizedGameRecordQueryRepository,
    private val onboardingPreferenceQueryRepository:
        PersonalizedOnboardingPreferenceQueryRepository,
    private val relatedGameProvider: PersonalizedRelatedGameProvider,
    private val candidateQueryRepository: PersonalizedRecommendationCandidateQueryRepository,
    private val genrePreferenceCalculator: PersonalizedGenrePreferenceCalculator,
    private val recordSourceSelector: PersonalizedRecordSourceSelector,
    private val relatedRankScoreCalculator: PersonalizedRelatedRankScoreCalculator,
    private val onboardingScoreCalculator: PersonalizedOnboardingScoreCalculator,
) {
    // 사용자별 맞춤 추천 결과를 API 응답 DTO로 변환해 반환
    fun recommend(userId: Long): List<PersonalizedGameRecommendationResponse> {
        val responses = recommendResults(userId).map { result ->
            PersonalizedGameRecommendationResponse(
                id = result.gameId,
                title = result.title,
                coverImageUrl = result.coverImageUrl,
                igdbRating = result.igdbRating,
                recommendationScore = result.recommendationScore,
                genres = java.util.List.copyOf(result.genres.map { genre ->
                    GenreResponse(id = genre.id, name = genre.name)
                }),
            )
        }
        return java.util.List.copyOf(responses)
    }

    // 사용 가능한 게임 기록을 우선하고, 사용할 수 없으면 온보딩 추천으로 전환
    private fun recommendResults(userId: Long): List<PersonalizedRecommendationResult> {
        val recordedGameIds = gameRecordQueryRepository.findRecordedGameIdsByUserId(userId)
        val records = gameRecordQueryRepository.findEligibleRecordsByUserId(userId)
        val sourceGameIds = recordSourceSelector.selectSourceGameIds(records)
        return if (sourceGameIds.isNotEmpty()) {
            recommendFromRecords(records, sourceGameIds, recordedGameIds)
        } else {
            recommendFromOnboarding(userId, recordedGameIds)
        }
    }

    // 기록 게임의 연관 추천 순위 점수와 상위 장르 가산점으로 추천 결과 생성
    private fun recommendFromRecords(
        records: List<PersonalizedGameRecordProjection>,
        sourceGameIds: List<Long>,
        recordedGameIds: List<Long>,
    ): List<PersonalizedRecommendationResult> {
        val relatedGames = relatedGameProvider.findTopFiveBySourceGameIds(sourceGameIds, recordedGameIds)
        val relatedScores = relatedRankScoreCalculator.calculate(relatedGames)
        if (relatedScores.isEmpty()) return emptyList()

        val topGenres = genrePreferenceCalculator.calculate(records).take(2)
        return buildResults(relatedScores) { genreIds -> calculateRecordGenreBonus(topGenres, genreIds) }
    }

    // 온보딩 선호 게임을 우선 후보로 사용하고, 선호 게임이 없으면 장르 후보를 조회
    private fun recommendFromOnboarding(
        userId: Long,
        recordedGameIds: List<Long>,
    ): List<PersonalizedRecommendationResult> {
        val preferredGameIds = onboardingPreferenceQueryRepository.findPreferredGameIds(userId)
        val preferredGenreIds = onboardingPreferenceQueryRepository.findPreferredGenreIds(userId)
        if (preferredGameIds.isEmpty() && preferredGenreIds.isEmpty()) return emptyList()

        val relatedScores = if (preferredGameIds.isNotEmpty()) {
            val excludedGameIds = (recordedGameIds + preferredGameIds).distinct()
            val relatedGames = relatedGameProvider.findTopFiveBySourceGameIds(preferredGameIds, excludedGameIds)
            onboardingScoreCalculator.calculateRelatedScores(preferredGameIds, relatedGames)
        } else {
            // 장르 전용 조회는 게임 ID로 집계하므로 후보 ID가 중복되지 않는다.
            candidateQueryRepository.findTopFiveIdsByPreferredGenres(preferredGenreIds, recordedGameIds)
                .associateWith { 0 }
        }
        if (relatedScores.isEmpty()) return emptyList()

        return buildResults(relatedScores) { genreIds ->
            onboardingScoreCalculator.calculateGenreBonus(preferredGenreIds, genreIds)
        }
    }

    // 후보 정보를 일괄 조회하고 최종 추천 점수를 계산해 상위 5개 반환
    private fun buildResults(
        relatedScores: Map<Long, Int>,
        genreBonusCalculator: (List<Long>) -> Int,
    ): List<PersonalizedRecommendationResult> {
        val rowsByGameId = candidateQueryRepository.findAllByGameIds(relatedScores.keys.toList())
            .groupBy { it.gameId }
        return relatedScores.mapNotNull { (gameId, relatedScore) ->
            createResult(
                gameId = gameId,
                relatedScore = relatedScore,
                rows = rowsByGameId[gameId].orEmpty(),
                genreBonusCalculator = genreBonusCalculator,
            )
        }.sortedWith(resultComparator()).take(5)
    }

    // 한 후보의 순위 점수, 장르 가산점, IGDB 평점 점수를 합산
    private fun createResult(
        gameId: Long,
        relatedScore: Int,
        rows: List<PersonalizedRecommendationCandidateProjection>,
        genreBonusCalculator: (List<Long>) -> Int,
    ): PersonalizedRecommendationResult? {
        // 후보 조회 사이에 게임이 사라지거나 공개 대상에서 제외되면 결과에 포함하지 않는다.
        val game = rows.firstOrNull() ?: return null
        val genres = rows.mapNotNull { row ->
            // LEFT JOIN에서 장르가 없는 게임은 응답에 빈 장르 목록을 제공한다.
            row.genreId?.let { genreId ->
                PersonalizedRecommendationGenre(
                    id = genreId,
                    name = requireNotNull(row.genreName) { "조회한 장르의 이름이 없습니다." },
                )
            }
        }.distinct()
        val genreBonus = genreBonusCalculator(genres.map { it.id })
        val igdbScore = game.igdbRating?.multiply(IGDB_RATING_WEIGHT) ?: BigDecimal.ZERO
        val recommendationScore = BigDecimal.valueOf((relatedScore + genreBonus).toLong()).add(igdbScore)
        return PersonalizedRecommendationResult(
            gameId = gameId,
            title = game.title,
            coverImageUrl = game.coverImageUrl,
            igdbRating = game.igdbRating,
            averageRating = game.averageRating,
            releaseDate = game.releaseDate,
            recommendationScore = recommendationScore,
            genres = java.util.List.copyOf(genres),
        )
    }

    // 기록 기반 추천의 1순위 장르에 10점, 2순위 장르에 5점 부여
    private fun calculateRecordGenreBonus(
        topGenres: List<PersonalizedGenrePreference>,
        candidateGenreIds: List<Long>,
    ): Int {
        var genreBonus = 0
        if (topGenres.isNotEmpty() && topGenres[0].genreId in candidateGenreIds) {
            genreBonus += 10
        }
        if (topGenres.size > 1 && topGenres[1].genreId in candidateGenreIds) {
            genreBonus += 5
        }
        return genreBonus
    }

    // 최종 점수, 사용자 평균 평점, 출시일, 게임 ID 순서로 정렬
    private fun resultComparator(): Comparator<PersonalizedRecommendationResult> {
        return compareByDescending<PersonalizedRecommendationResult> { it.recommendationScore }
            .thenBy(nullsLast(reverseOrder<Double>())) { it.averageRating }
            .thenBy(nullsLast(reverseOrder<java.time.LocalDate>())) { it.releaseDate }
            .thenBy { it.gameId }
    }

    companion object {
        private val IGDB_RATING_WEIGHT = BigDecimal("0.5")
    }
}
