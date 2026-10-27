package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedGameRecordProjection
import com.gamelog.nbe121423355.domain.game.service.model.PersonalizedGenrePreference
import java.math.BigDecimal
import org.springframework.stereotype.Component

// 게임 기록의 장르 기여도와 별점을 이용해 사용자 장르 선호도를 계산
@Component
class PersonalizedGenrePreferenceCalculator {
    // 게임별 장르 기여도를 합산해 장르 선호도를 계산하고 높은 순서로 반환
    fun calculate(
        records: List<PersonalizedGameRecordProjection>
    ): List<PersonalizedGenrePreference> {
        // 게임별 합산 순서를 유지하여 부동소수점 집계 결과를 보존한다.
        val recordsByGame = hashMapOf<Long, MutableList<PersonalizedGameRecordProjection>>()
        for (record in records) {
            recordsByGame.computeIfAbsent(record.gameId) { mutableListOf() }.add(record)
        }
        val totalsByGenre = hashMapOf<Long, GenreTotals>()

        var gamesWithGenres = 0

        for (gameRecords in recordsByGame.values) {
            val genreIds = gameRecords.mapNotNull { it.genreId }.distinct()

            if (genreIds.isEmpty()) {
                continue
            }

            gamesWithGenres++
            val contribution = 1.0 / genreIds.size
            val rating = gameRecords.first().rating

            for (genreId in genreIds) {
                totalsByGenre
                    .getOrPut(genreId) { GenreTotals() }
                    .add(contribution, rating)
            }
        }

        if (gamesWithGenres == 0) {
            return emptyList()
        }

        val preferences = totalsByGenre.map { (genreId, totals) ->
            val playRatio = totals.playWeight / gamesWithGenres
            val averageRating = totals.averageRating
            val ratingScore = if (averageRating == null) 0.0 else averageRating / 5.0 * 60.0
            PersonalizedGenrePreference(
                genreId = genreId,
                playRatio = playRatio,
                averageRating = averageRating,
                score = playRatio * 40.0 + ratingScore,
            )
        }.sortedWith(compareByDescending<PersonalizedGenrePreference> { it.score }.thenBy { it.genreId })
        return java.util.List.copyOf(preferences)
    }

    private class GenreTotals {
        var playWeight = 0.0
        private var ratedWeight = 0.0
        private var weightedRatingSum = 0.0

        // 별점이 없어도 플레이 기여도는 더하고, 별점 평균에는 포함하지 않음
        fun add(contribution: Double, rating: BigDecimal?) {
            playWeight += contribution

            if (rating != null) {
                ratedWeight += contribution
                weightedRatingSum += rating.toDouble() * contribution
            }
        }

        // 해당 장르에 별점이 있는 기록이 없으면 평균 별점을 반환하지 않음
        val averageRating: Double?
            get() = if (ratedWeight == 0.0) null else weightedRatingSum / ratedWeight
    }
}
