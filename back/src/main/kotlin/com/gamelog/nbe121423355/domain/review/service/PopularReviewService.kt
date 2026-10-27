package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.review.dto.response.PopularReviewResponse
import com.gamelog.nbe121423355.domain.review.repository.ReviewLikeRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PopularReviewService(private val reviewLikeRepository: ReviewLikeRepository) {
    fun getPopularReviews(size: Int): List<PopularReviewResponse> {
        validateSize(size)
        return reviewLikeRepository
            .findPopularReviews(PageRequest.of(0, size))
            .map { review ->
                PopularReviewResponse(
                    reviewId = review.reviewId,
                    userId = review.userId,
                    nickname = review.nickname,
                    profileImageUrl = review.profileImageUrl,
                    gameId = review.gameId,
                    gameTitle = review.gameTitle,
                    gameCoverImageUrl = review.gameCoverImageUrl,
                    rating = review.rating,
                    content = review.content,
                    spoiler = review.spoiler,
                    likeCount = review.likeCount,
                    createdDate = review.createdDate,
                )
            }
    }

    private fun validateSize(size: Int) {
        if (size !in 1..MAX_SIZE) {
            throw ServiceException("400-10", "조회 개수는 1 이상 50 이하여야 합니다.")
        }
    }

    companion object {
        private const val MAX_SIZE = 50
    }
}
