package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameDetailResponse.GenreResponse
import com.gamelog.nbe121423355.domain.game.dto.PersonalizedGameRecommendationResponse
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedGameRecordQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedOnboardingPreferenceQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRecommendationCandidateQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedGameRecordProjection
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRecommendationCandidateProjection
import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import com.gamelog.nbe121423355.domain.game.service.model.PersonalizedGenrePreference
import com.gamelog.nbe121423355.domain.game.service.model.PersonalizedRecommendationGenre
import com.gamelog.nbe121423355.domain.game.service.model.PersonalizedRecommendationResult
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.util.ArrayList
import java.util.Comparator
import java.util.function.ToIntFunction
import java.util.stream.Collectors
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 게임 기록을 우선하고 온보딩을 후순위로 사용해 맞춤 추천 결과를 생성
@Service
@Transactional(readOnly = true)
class PersonalizedGameRecommendationService
constructor(
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
    fun recommend(userId: Long?): MutableList<PersonalizedGameRecommendationResponse> {
        return recommendResults(userId)
            .stream()
            .map({ result ->
                PersonalizedGameRecommendationResponse(
                    result.gameId,
                    result.title,
                    result.coverImageUrl,
                    result.igdbRating,
                    result.recommendationScore,
                    result.genres
                        .stream()
                        .map({ genre ->
                            GenreResponse(
                                genre.id,
                                genre.name,
                            )
                        })
                        .toList(),
                )
            })
            .toList()
    }

    // 사용 가능한 게임 기록을 우선하고, 사용할 수 없으면 온보딩 추천으로 전환
    private fun recommendResults(userId: Long?): MutableList<PersonalizedRecommendationResult> {
        val recordedGameIds = gameRecordQueryRepository.findRecordedGameIdsByUserId(userId)
        val records: MutableList<PersonalizedGameRecordProjection> =
            gameRecordQueryRepository.findEligibleRecordsByUserId(userId)
        if (!records.isEmpty()) {
            val sourceGameIds = recordSourceSelector.selectSourceGameIds(records)
            if (!sourceGameIds.isEmpty()) {
                return recommendFromRecords(records, sourceGameIds, recordedGameIds)
            }
        }
        return recommendFromOnboarding(userId, recordedGameIds)
    }

    // 기록 게임의 연관 추천 순위 점수와 상위 장르 가산점으로 추천 결과 생성
    private fun recommendFromRecords(
        records: MutableList<PersonalizedGameRecordProjection>,
        sourceGameIds: MutableList<Long>,
        recordedGameIds: MutableList<Long>,
    ): MutableList<PersonalizedRecommendationResult> {
        val relatedGames: MutableList<PersonalizedRelatedGameProjection> =
            relatedGameProvider.findTopFiveBySourceGameIds(sourceGameIds, recordedGameIds)
        val relatedScores: MutableMap<Long, Int> =
            relatedRankScoreCalculator.calculate(relatedGames)
        if (relatedScores.isEmpty()) {
            return java.util.List.of()
        }
        val topGenres: MutableList<PersonalizedGenrePreference> =
            genrePreferenceCalculator.calculate(records).stream().limit(2).toList()
        return buildResults(
            relatedScores,
            ToIntFunction { genreIds -> calculateRecordGenreBonus(topGenres, genreIds) },
        )
    }

    // 온보딩 선호 게임을 우선 후보로 사용하고, 선호 게임이 없으면 장르 후보를 조회
    private fun recommendFromOnboarding(
        userId: Long?,
        recordedGameIds: MutableList<Long>,
    ): MutableList<PersonalizedRecommendationResult> {
        val preferredGameIds: MutableList<Long> =
            onboardingPreferenceQueryRepository.findPreferredGameIds(userId)
        val preferredGenreIds: MutableList<Long> =
            onboardingPreferenceQueryRepository.findPreferredGenreIds(userId)
        if (preferredGameIds.isEmpty() && preferredGenreIds.isEmpty()) {
            return java.util.List.of()
        }
        val relatedScores: MutableMap<Long, Int>
        if (!preferredGameIds.isEmpty()) {
            val excludedGameIds: MutableList<Long> = ArrayList(recordedGameIds)
            excludedGameIds.addAll(preferredGameIds)
            val relatedGames: MutableList<PersonalizedRelatedGameProjection> =
                relatedGameProvider.findTopFiveBySourceGameIds(
                    preferredGameIds,
                    excludedGameIds.stream().distinct().toList(),
                )
            relatedScores =
                onboardingScoreCalculator.calculateRelatedScores(preferredGameIds, relatedGames)
        } else {
            val genreCandidateIds: MutableList<Long> =
                candidateQueryRepository.findTopFiveIdsByPreferredGenres(
                    preferredGenreIds,
                    recordedGameIds,
                )
            relatedScores =
                genreCandidateIds
                    .stream()
                    .collect(Collectors.toMap({ gameId -> gameId }, { gameId -> 0 }))
        }
        if (relatedScores.isEmpty()) {
            return java.util.List.of()
        }
        return buildResults(
            relatedScores,
            ToIntFunction { genreIds ->
                onboardingScoreCalculator.calculateGenreBonus(preferredGenreIds, genreIds)
            },
        )
    }

    // 후보 정보를 일괄 조회하고 최종 추천 점수를 계산해 상위 5개 반환
    private fun buildResults(
        relatedScores: MutableMap<Long, Int>,
        genreBonusCalculator: ToIntFunction<MutableList<Long>>,
    ): MutableList<PersonalizedRecommendationResult> {
        val candidateRows: MutableList<PersonalizedRecommendationCandidateProjection> =
            candidateQueryRepository.findAllByGameIds(ArrayList(relatedScores.keys))
        val rowsByGameId:
            MutableMap<Long, MutableList<PersonalizedRecommendationCandidateProjection>> =
            candidateRows
                .stream()
                .collect(
                    Collectors.groupingBy(PersonalizedRecommendationCandidateProjection::gameId)
                )
        return relatedScores.entries
            .mapNotNull { entry ->
                createResult(
                    entry.key,
                    entry.value,
                    rowsByGameId.get(entry.key),
                    genreBonusCalculator,
                )
            }
            .sortedWith(resultComparator())
            .take(5)
            .toMutableList()
    }

    // 한 후보의 순위 점수, 장르 가산점, IGDB 평점 점수를 합산
    private fun createResult(
        gameId: Long?,
        relatedScore: Int,
        rows: MutableList<PersonalizedRecommendationCandidateProjection>?,
        genreBonusCalculator: ToIntFunction<MutableList<Long>>,
    ): PersonalizedRecommendationResult? {
        if (rows == null || rows.isEmpty()) {
            return null
        }
        val game: PersonalizedRecommendationCandidateProjection = rows.first()
        val genres: MutableList<PersonalizedRecommendationGenre> =
            rows
                .stream()
                .filter({ row -> row.genreId != null })
                .map({ row ->
                    PersonalizedRecommendationGenre(requireNotNull(row.genreId), row.genreName)
                })
                .distinct()
                .toList()
        val genreIds: MutableList<Long> =
            genres.stream().map(PersonalizedRecommendationGenre::id).toList()
        val genreBonus: Int = genreBonusCalculator.applyAsInt(genreIds)
        val igdbScore: BigDecimal? =
            if (game.igdbRating == null) BigDecimal.ZERO
            else game.igdbRating.multiply(PersonalizedGameRecommendationService.IGDB_RATING_WEIGHT)
        val recommendationScore: BigDecimal? =
            BigDecimal.valueOf((relatedScore + genreBonus).toLong()).add(igdbScore)
        return PersonalizedRecommendationResult(
            gameId,
            game.title,
            game.coverImageUrl,
            game.igdbRating,
            game.averageRating,
            game.releaseDate,
            recommendationScore,
            genres,
        )
    }

    // 기록 기반 추천의 1순위 장르에 10점, 2순위 장르에 5점 부여
    private fun calculateRecordGenreBonus(
        topGenres: MutableList<PersonalizedGenrePreference>,
        candidateGenreIds: MutableList<Long>,
    ): Int {
        var genreBonus = 0
        if (!topGenres.isEmpty() && candidateGenreIds.contains(topGenres.get(0).genreId)) {
            genreBonus += 10
        }
        if (topGenres.size > 1 && candidateGenreIds.contains(topGenres.get(1).genreId)) {
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
        private val IGDB_RATING_WEIGHT: BigDecimal = BigDecimal("0.5")
    }
}
