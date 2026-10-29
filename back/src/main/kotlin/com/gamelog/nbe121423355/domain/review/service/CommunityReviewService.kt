package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.review.dto.response.CommunityFeedResponse
import com.gamelog.nbe121423355.domain.review.dto.response.CommunityReviewResponse
import com.gamelog.nbe121423355.domain.review.repository.CommunityReviewRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CommunityReviewService(private val repository: CommunityReviewRepository) {
    fun following(viewerId: Long, page: Int): CommunityFeedResponse {
        if (page < 0 || page > 100000) {
            throw ServiceException("400-10", "페이지는 0 이상 100000 이하여야 합니다.")
        }
        val reviews = repository.findFollowing(viewerId, PageRequest.of(page, 20))
        return CommunityFeedResponse(
            reviews.content.map { CommunityReviewResponse.from(it) },
            reviews.hasNext(),
            repository.hasFollowing(viewerId),
        )
    }

    fun popular(viewerId: Long): CommunityFeedResponse {
        // 기존 JPA 감사 필드와 동일한 서버 시간 기준으로 비교한다.
        val now = LocalDateTime.now()
        val reviews =
            repository.findPopular(viewerId, now.minusHours(168), now, PageRequest.of(0, 20))
        return CommunityFeedResponse(
            reviews.map { CommunityReviewResponse.from(it) },
            false,
            false,
        )
    }
}
