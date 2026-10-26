package com.gamelog.nbe121423355.domain.game.repository.projection

import java.math.BigDecimal
import java.time.LocalDate

// 최종 점수 계산에 필요한 추천 후보의 게임 정보, 사용자 평균 평점, 장르를 매핑
@JvmRecord
data class PersonalizedRecommendationCandidateProjection(
    val gameId: Long,
    val title: String,
    val coverImageUrl: String?,
    val igdbRating: BigDecimal?,
    val averageRating: Double?,
    val releaseDate: LocalDate?,
    val genreId: Long?,
    val genreName: String?,
)
