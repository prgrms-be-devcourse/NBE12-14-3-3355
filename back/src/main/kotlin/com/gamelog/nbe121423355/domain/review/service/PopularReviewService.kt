package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.review.dto.response.PopularReviewResponse
import com.gamelog.nbe121423355.domain.review.repository.ReviewLikeRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PopularReviewService constructor(private val reviewLikeRepository: ReviewLikeRepository) {
    fun getPopularReviews(size: Int): MutableList<PopularReviewResponse> {
        validateSize(size)
        return reviewLikeRepository
            .findPopularReviews(PageRequest.of(0, size))
            .stream()
            .map({ review ->
                PopularReviewResponse(
                    review.reviewId,
                    review.userId,
                    review.nickname,
                    review.profileImageUrl,
                    review.gameId,
                    review.gameTitle,
                    review.gameCoverImageUrl,
                    review.rating,
                    review.content,
                    review.spoiler,
                    review.likeCount,
                    review.createdDate,
                )
            })
            .toList()
    }

    private fun validateSize(size: Int) {
        if (size < 1 || size > MAX_SIZE) {
            throw ServiceException("400-10", "조회 개수는 1 이상 50 이하여야 합니다.")
        }
    }

    companion object {
        private const val MAX_SIZE = 50
    }
}
