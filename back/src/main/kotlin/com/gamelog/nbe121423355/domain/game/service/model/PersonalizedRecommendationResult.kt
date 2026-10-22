package com.gamelog.nbe121423355.domain.game.service.model

import java.math.BigDecimal
import java.time.LocalDate

// 맞춤 추천의 최종 점수와 정렬 정보를 담는 서비스 내부 결과
@JvmRecord
data class PersonalizedRecommendationResult(
    val gameId: Long?,
    val title: String?,
    val coverImageUrl: String?,
    val igdbRating: BigDecimal?,
    val averageRating: Double?,
    val releaseDate: LocalDate?,
    val recommendationScore: BigDecimal?,
    val genres: MutableList<PersonalizedRecommendationGenre>,
) {}
