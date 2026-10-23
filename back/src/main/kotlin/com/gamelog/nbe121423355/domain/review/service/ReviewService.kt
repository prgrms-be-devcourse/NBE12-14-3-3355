package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.response.DetailedReviewResponse
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewPageResponse
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewResponse
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.repository.ReviewLikeRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameDto
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import java.util.Objects
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReviewService
constructor(
    private val changeRecorder: RelatedGameChangeRecorder,
    private val reviewRepository: ReviewRepository,
    private val reviewLikeRepository: ReviewLikeRepository,
    private val userGameRepository: UserGameRepository,
    private val reviewCommentService: ReviewCommentService,
) {
    fun getMyDetailedReview(userId: Long, gameId: Long): DetailedReviewResponse {
        val userGame = userGameRepository.findByUser_IdAndGame_Id(userId, gameId)
        if (userGame == null) {
            return DetailedReviewResponse(null, null)
        }
        val review =
            reviewRepository
                .findByUserGame_Id(checkNotNull(userGame.id))
                .map({ review: Review -> this.toReviewResponse(review) })
                .orElse(null)
        return DetailedReviewResponse(UserGameDto(userGame), review)
    }

    @Transactional
    fun saveReview(userId: Long, userGameId: Long, request: ReviewSaveRequest): ReviewResponse {
        validateReviewContent(request)
        val userGame = getUserGame(userGameId)
        validateOwner(userId, userGame)
        changeRecorder.record(requireNotNull(userGame.game.id), RelatedGameChangeReason.REVIEW)
        // 리뷰가 있으면 수정하고 없으면 새로 작성.
        val review = reviewRepository.findIncludingDeletedByUserGameId(userGameId).orElse(null)
        if (review != null) {
            if (review.isDeletedByUser) {
                validateVersionWhenProvided(review, request.version)
                review.restore(request.rating, request.content, request.spoiler)
            } else if (!review.isActive) {
                throw ServiceException(
                    "409-5",
                    "관리자에 의해 숨김 처리된 리뷰는 다시 작성할 수 없습니다.",
                )
            } else {
                validateVersion(review, request.version)
                review.edit(request.rating, request.content, request.spoiler)
            }
            reviewRepository.flush()
            return toReviewResponse(review)
        }
        val newReview = Review(userGame, request.rating, request.content, request.spoiler)
        val savedReview = reviewRepository.save(newReview)
        return toReviewResponse(savedReview)
    }

    fun getReview(reviewId: Long): ReviewResponse {
        return toReviewResponse(getReviewEntity(reviewId))
    }

    fun getGameReviews(gameId: Long, pageable: Pageable): ReviewPageResponse {
        val reviews: Page<Review> = reviewRepository.findByUserGame_Game_Id(gameId, pageable)
        return toReviewPageResponse(reviews, reviewLikeRepository.countVisibleLikesByGameId(gameId))
    }

    fun getUserReviews(userId: Long, pageable: Pageable): ReviewPageResponse {
        val reviews: Page<Review> = reviewRepository.findByUserGame_User_Id(userId, pageable)
        return toReviewPageResponse(reviews, reviewLikeRepository.countVisibleLikesByUserId(userId))
    }

    @Transactional
    fun updateReview(userId: Long, reviewId: Long, request: ReviewSaveRequest): ReviewResponse {
        validateReviewContent(request)
        val review = getReviewEntity(reviewId)
        validateOwner(userId, review.userGame)
        validateVersion(review, request.version)
        // 변경된 리뷰를 자동으로 반영.
        review.edit(request.rating, request.content, request.spoiler)
        reviewRepository.flush()
        changeRecorder.record(requireNotNull(review.userGame.game.id), RelatedGameChangeReason.REVIEW)
        return toReviewResponse(review)
    }

    @Transactional
    fun deleteReview(userId: Long, reviewId: Long, version: Long?): Long {
        val review =
            reviewRepository
                .findActiveForUpdate(reviewId)
                .orElseThrow({ ServiceException("404-3", "리뷰를 찾을 수 없습니다.") })
        validateOwner(userId, review.userGame)
        validateVersion(review, version)
        reviewCommentService.purge(reviewId)
        review.deleteByUser()
        changeRecorder.record(requireNotNull(review.userGame.game.id), RelatedGameChangeReason.REVIEW)
        reviewRepository.flush()
        return checkNotNull(review.userGame.id)
    }

    // 회원탈퇴 트랜잭션 안에서만 호출 (라이브러리 통계 유지를 위해 라이브러리 상태는 재계산하지 않음)
    @Transactional(propagation = MANDATORY)
    fun hideAllByWithdrawnUser(userId: Long) {
        changeRecorder.recordWithdrawnReviews(userId)
        reviewRepository.hideAllByUserId(userId, LocalDateTime.now())
    }

    private fun getUserGame(userGameId: Long): UserGame {
        return userGameRepository
            .findById(userGameId)
            .orElseThrow({ ServiceException("404-2", "게임 기록을 찾을 수 없습니다.") })
    }

    private fun getReviewEntity(reviewId: Long): Review {
        return reviewRepository
            .findActiveById(reviewId)
            .orElseThrow({ ServiceException("404-3", "리뷰를 찾을 수 없습니다.") })
    }

    fun getReviewByUserGameId(userGameId: Long): ReviewResponse? {
        return reviewRepository
            .findByUserGame_Id(userGameId)
            .map({ review: Review -> this.toReviewResponse(review) })
            .orElse(null)
    }

    private fun toReviewResponse(review: Review): ReviewResponse {
        return ReviewResponse.from(review, reviewLikeRepository.countByReview_Id(checkNotNull(review.id)))
    }

    private fun toReviewPageResponse(
        reviews: Page<Review>,
        totalLikes: Long,
    ): ReviewPageResponse {
        val reviewIds = reviews.content.map { requireNotNull(it.id) }
        val likeCounts: Map<Long, Long> =
            if (reviewIds.isEmpty()) emptyMap()
            else
                reviewLikeRepository
                    .findLikeCountsByReviewIds(reviewIds)
                    .associate { checkNotNull(it.reviewId) to it.likeCount }
        return ReviewPageResponse.from(reviews, likeCounts, totalLikes)
    }

    private fun validateReviewContent(request: ReviewSaveRequest) {
        if (!request.hasReviewContent()) {
            throw ServiceException("400-4", "별점 또는 리뷰 내용 중 하나는 입력해야 합니다.")
        }
    }

    private fun validateOwner(userId: Long, userGame: UserGame) {
        if (!Objects.equals(userGame.user.id, userId)) {
            throw ServiceException("403-1", "본인의 리뷰만 작성하거나 변경할 수 있습니다.")
        }
    }

    private fun validateVersion(review: Review, requestedVersion: Long?) {
        if (requestedVersion == null || !Objects.equals(review.version, requestedVersion)) {
            throw staleReviewException()
        }
    }

    private fun validateVersionWhenProvided(review: Review, requestedVersion: Long?) {
        if (requestedVersion != null && !Objects.equals(review.version, requestedVersion)) {
            throw staleReviewException()
        }
    }

    private fun staleReviewException(): ServiceException {
        return ServiceException(
            "409-6",
            "다른 곳에서 리뷰가 변경되었습니다. 최신 내용을 다시 불러온 후 시도해 주세요.",
        )
    }
}
