package com.gamelog.nbe121423355.domain.usergame.dto

@JvmRecord
data class UserProfileResponse(
    val userId: Long,
    val isMe: Boolean,
    val isFollowing: Boolean,
    val favorite: List<UserFavoriteGameResponse>,
    val stats: ProfileStatsDto,
    val scatterData: List<UserGameScatterDto>,
    val tasteResponse: UserGameTasteDto,
    val genreDistribution: List<UserGameGenreDistributionResponse>,
    val recentGames: List<UserGameListResponse>,
    val recentReviews: List<RecentReviewResponse>,
) {}
