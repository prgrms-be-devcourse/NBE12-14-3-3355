package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedGameRecordProjection
import com.gamelog.nbe121423355.domain.game.service.model.PersonalizedGenrePreference
import java.math.BigDecimal
import java.util.Comparator
import java.util.HashMap
import java.util.stream.Collectors
import org.springframework.stereotype.Component

// 게임 기록의 장르 기여도와 별점을 이용해 사용자 장르 선호도를 계산
@Component
class PersonalizedGenrePreferenceCalculator {
    // 게임별 장르 기여도를 합산해 장르 선호도를 계산하고 높은 순서로 반환
    fun calculate(
        records: MutableList<PersonalizedGameRecordProjection>
    ): MutableList<PersonalizedGenrePreference> {
        val recordsByGame: MutableMap<Long, MutableList<PersonalizedGameRecordProjection>> =
            records
                .stream()
                .collect(Collectors.groupingBy(PersonalizedGameRecordProjection::gameId))

        val totalsByGenre: MutableMap<Long, GenreTotals> = HashMap()

        var gamesWithGenres = 0

        for (gameRecords in recordsByGame.values) {
            val genreIds = gameRecords.mapNotNull { it.genreId }.distinct().toMutableList()

            if (genreIds.isEmpty()) {
                continue
            }

            gamesWithGenres++
            val contribution: Double = 1.0 / genreIds.size
            val rating: BigDecimal? = gameRecords.first().rating

            for (genreId in genreIds) {
                totalsByGenre
                    .computeIfAbsent(genreId, { ignored -> GenreTotals() })
                    .add(contribution, rating)
            }
        }

        if (gamesWithGenres == 0) {
            return java.util.List.of()
        }

        val totalGamesWithGenres = gamesWithGenres

        return totalsByGenre.entries
            .stream()
            .map({ entry ->
                val totals: GenreTotals = entry.value
                val playRatio = totals.playWeight / totalGamesWithGenres
                val averageRating: Double? = totals.averageRating()
                val ratingScore = if (averageRating == null) 0.0 else averageRating / 5.0 * 60.0
                PersonalizedGenrePreference(
                    entry.key,
                    playRatio,
                    averageRating,
                    playRatio * 40.0 + ratingScore,
                )
            })
            .sorted(
                Comparator.comparingDouble(PersonalizedGenrePreference::score)
                    .reversed()
                    .thenComparing(PersonalizedGenrePreference::genreId)
            )
            .toList()
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
        fun averageRating(): Double? {
            return (if (ratedWeight == 0.0) null else weightedRatingSum / ratedWeight)
        }
    }
}
