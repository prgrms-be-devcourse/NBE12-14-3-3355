package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.review.dto.response.PopularReviewResponse
import com.gamelog.nbe121423355.domain.review.repository.PopularReviewProjection
import com.gamelog.nbe121423355.domain.review.repository.ReviewLikeRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest

@ExtendWith(MockitoExtension::class)
class PopularReviewServiceTest {
    @Mock
    private lateinit var reviewLikeRepository: ReviewLikeRepository

    @InjectMocks
    private lateinit var popularReviewService: PopularReviewService

    @Test
    @DisplayName("인기 리뷰 Projection을 응답 DTO로 변환한다")
    fun getPopularReviewsMapsProjectionToResponse() {
        // given: 인기 리뷰 Projection 한 개를 준비한다.
        val review = mock(PopularReviewProjection::class.java)
        val createdDate = LocalDateTime.of(2026, 9, 22, 12, 0)
        `when`(review.reviewId).thenReturn(1L)
        `when`(review.userId).thenReturn(10L)
        `when`(review.nickname).thenReturn("리뷰 작성자")
        `when`(review.profileImageUrl).thenReturn("https://image.test/profile.jpg")
        `when`(review.gameId).thenReturn(20L)
        `when`(review.gameTitle).thenReturn("게임 제목")
        `when`(review.gameCoverImageUrl).thenReturn("https://image.test/game.jpg")
        `when`(review.rating).thenReturn(BigDecimal("4.5"))
        `when`(review.content).thenReturn("인기 리뷰 내용")
        `when`(review.spoiler).thenReturn(true)
        `when`(review.likeCount).thenReturn(15L)
        `when`(review.createdDate).thenReturn(createdDate)
        `when`(reviewLikeRepository.findPopularReviews(PageRequest.of(0, 5))).thenReturn(listOf(review))

        // when: 기본 크기로 인기 리뷰를 조회한다.
        val result = popularReviewService.getPopularReviews(5)

        // then: Projection의 모든 카드 정보가 응답 DTO에 매핑된다.
        assertThat(result).containsExactly(
            PopularReviewResponse(
                reviewId = 1L,
                userId = 10L,
                nickname = "리뷰 작성자",
                profileImageUrl = "https://image.test/profile.jpg",
                gameId = 20L,
                gameTitle = "게임 제목",
                gameCoverImageUrl = "https://image.test/game.jpg",
                rating = BigDecimal("4.5"),
                content = "인기 리뷰 내용",
                spoiler = true,
                likeCount = 15L,
                createdDate = createdDate,
            )
        )
    }

    @Test
    @DisplayName("인기 리뷰가 없으면 빈 목록을 반환한다")
    fun getPopularReviewsReturnsEmptyList() {
        // given: 인기 리뷰 조회 결과가 비어 있도록 준비한다.
        `when`(reviewLikeRepository.findPopularReviews(PageRequest.of(0, 5))).thenReturn(emptyList())

        // when: 인기 리뷰를 조회한다.
        val result = popularReviewService.getPopularReviews(5)

        // then: 빈 목록을 반환한다.
        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 51])
    @DisplayName("조회 개수가 허용 범위를 벗어나면 예외가 발생한다")
    fun getPopularReviewsRejectsInvalidSize(size: Int) {
        // given: 허용 범위를 벗어난 조회 개수를 준비한다.
        val invalidSize = size

        // when: 잘못된 조회 개수로 인기 리뷰 조회를 요청한다.
        val failure = catchThrowable { popularReviewService.getPopularReviews(invalidSize) }

        // then: 400 예외가 발생하고 Repository는 호출하지 않는다.
        assertThat(failure).isInstanceOfSatisfying(ServiceException::class.java) {
            assertThat(it.resultCode).isEqualTo("400-10")
        }
        verifyNoInteractions(reviewLikeRepository)
    }
}
