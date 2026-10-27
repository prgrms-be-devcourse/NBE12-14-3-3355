package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.response.DetailedReviewResponse
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewResponse
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameDto
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 게임 기록과 리뷰 변경이 함께 만드는 유스케이스의 트랜잭션 경계를 담당한다. 리뷰 변경 뒤 라이브러리 포함 여부를 다시 계산하는 순서도 이 서비스가 관리한다. */
@Service
class GameRecordReviewService
constructor(
    private val userGameService: UserGameService,
    private val reviewService: ReviewService,
) {
    @Transactional
    fun saveDetailedReview(
        userId: Long,
        gameId: Long,
        request: DetailedReviewSaveRequest,
    ): DetailedReviewResponse {
        val userGameResult =
            userGameService.prepareGameRecordForReview(
                userId,
                gameId,
                requireNotNull(request.userGame),
            )
        val review: ReviewResponse?
        if (request.review == null || !request.review.hasReviewContent()) {
            review = reviewService.getReviewByUserGameId(checkNotNull(userGameResult.userGame.id))
        } else {
            review =
                reviewService.saveReview(
                    userId,
                    requireNotNull(userGameResult.userGame.id),
                    request.review,
                )
        }
        userGameService.synchronizeLibraryState(userGameResult.userGame)
        return DetailedReviewResponse(UserGameDto(userGameResult.userGame), review)
    }

    @Transactional
    fun saveReview(userId: Long, userGameId: Long, request: ReviewSaveRequest): ReviewResponse {
        val review = reviewService.saveReview(userId, userGameId, request)
        userGameService.synchronizeLibraryState(requireNotNull(review.userGameId))
        return review
    }

    @Transactional
    fun updateReview(userId: Long, reviewId: Long, request: ReviewSaveRequest): ReviewResponse {
        val review = reviewService.updateReview(userId, reviewId, request)
        userGameService.synchronizeLibraryState(requireNotNull(review.userGameId))
        return review
    }

    @Transactional
    fun deleteReview(userId: Long, reviewId: Long, version: Long?) {
        val userGameId = reviewService.deleteReview(userId, reviewId, version)
        userGameService.synchronizeLibraryState(userGameId)
    }
}
