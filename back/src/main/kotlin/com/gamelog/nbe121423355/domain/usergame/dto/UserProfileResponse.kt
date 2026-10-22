package com.gamelog.nbe121423355.domain.usergame.dto

@JvmRecord
data class UserProfileResponse(
    val userId: Long?,
    val isMe: Boolean,
    val isFollowing: Boolean,
    val favorite: MutableList<UserFavoriteGameResponse>,
    val stats: ProfileStatsDto?,
    val scatterData: MutableList<UserGameScatterDto>,
    val tasteResponse: UserGameTasteDto?,
    val genreDistribution: MutableList<UserGameGenreDistributionResponse>,
    val recentGames: MutableList<UserGameListResponse>,
    val recentReviews: MutableList<RecentReviewResponse>?,
) {}
