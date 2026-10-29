package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import com.gamelog.nbe121423355.domain.notification.entity.NotificationTarget
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewLikeResponse
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewLike
import com.gamelog.nbe121423355.domain.review.repository.ReviewLikeRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReviewLikeService
constructor(
    private val notificationService: NotificationService,
    private val reviewLikeRepository: ReviewLikeRepository,
    private val reviewRepository: ReviewRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun addLike(userId: Long, reviewId: Long): ReviewLikeResponse {
        val review = getReview(reviewId)
        val user = getUser(userId)
        // 같은 리뷰에 좋아요를 한 번만 누를 수 있음.
        if (reviewLikeRepository.existsByReview_IdAndUser_Id(reviewId, userId)) {
            throw ServiceException("409-2", "이미 좋아요를 누른 리뷰입니다.")
        }
        reviewLikeRepository.save(ReviewLike(review, user))
        notificationService.create(
            recipients = setOf(requireNotNull(review.userGame.user.id)),
            actorId = userId,
            kind = NotificationKind.REVIEW_LIKE,
            targetType = NotificationTarget.REVIEW,
            targetId = reviewId,
            sourceKey = "review-like:$reviewId:$userId",
            reviewId = reviewId,
            gameTitle = review.userGame.game.title,
        )
        return createResponse(reviewId, true)
    }

    @Transactional
    fun removeLike(userId: Long, reviewId: Long): ReviewLikeResponse {
        getReview(reviewId)
        if (!reviewLikeRepository.existsByReview_IdAndUser_Id(reviewId, userId)) {
            throw ServiceException("404-6", "좋아요 기록을 찾을 수 없습니다.")
        }
        reviewLikeRepository.deleteByReview_IdAndUser_Id(reviewId, userId)
        notificationService.cancel("review-like:$reviewId:$userId")
        return createResponse(reviewId, false)
    }

    fun getLikeStatus(userId: Long?, reviewId: Long): ReviewLikeResponse {
        getReview(reviewId)
        val liked =
            userId != null && reviewLikeRepository.existsByReview_IdAndUser_Id(reviewId, userId)
        return createResponse(reviewId, liked)
    }

    private fun createResponse(reviewId: Long, liked: Boolean): ReviewLikeResponse {
        val likeCount = reviewLikeRepository.countByReview_Id(reviewId)
        return ReviewLikeResponse(reviewId, likeCount, liked)
    }

    private fun getReview(reviewId: Long): Review {
        return reviewRepository
            .findActiveById(reviewId)
            ?: throw ServiceException("404-3", "리뷰를 찾을 수 없습니다.")
    }

    private fun getUser(userId: Long): User {
        return userRepository
            .findById(userId)
            .orElseThrow({ ServiceException("404-4", "사용자를 찾을 수 없습니다.") })
    }
}
