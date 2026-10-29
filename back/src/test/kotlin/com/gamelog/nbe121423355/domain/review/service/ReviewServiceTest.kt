package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewLikeCountProjection
import com.gamelog.nbe121423355.domain.review.repository.ReviewLikeRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.math.BigDecimal
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class ReviewServiceTest {
    @Mock private lateinit var reviewRepository: ReviewRepository
    @Mock private lateinit var reviewLikeRepository: ReviewLikeRepository
    @Mock private lateinit var reviewCommentService: ReviewCommentService
    @Mock private lateinit var userGameRepository: UserGameRepository
    @Mock private lateinit var userGame: UserGame
    @Mock private lateinit var user: User
    @Mock private lateinit var game: Game
    @Mock private lateinit var changeRecorder: RelatedGameChangeRecorder
    @InjectMocks private lateinit var reviewService: ReviewService

    @DisplayName("직접 리뷰 저장 요청이 비어 있으면 거절한다")
    @Test
    fun rejectEmptyDirectReviewRequest() {
        val emptyReview = ReviewSaveRequest(null, " ", false)

        assertThatThrownBy { reviewService.saveReview(USER_ID, USER_GAME_ID, emptyReview) }
            .isInstanceOf(ServiceException::class.java)
            .hasMessage("별점 또는 리뷰 내용 중 하나는 입력해야 합니다.")

        verify(userGameRepository, never()).findById(anyLong())
        verify(reviewRepository, never()).save(anyReview())
    }

    @DisplayName("새 리뷰를 작성한다")
    @Test
    fun saveNewReview() {
        prepareOwnedUserGame()
        `when`(userGameRepository.findById(USER_GAME_ID)).thenReturn(Optional.of(userGame))
        `when`(reviewRepository.findIncludingDeletedByUserGameId(USER_GAME_ID)).thenReturn(null)
        `when`(reviewRepository.save(anyReview())).thenAnswer { invocation ->
            invocation.getArgument<Review>(0).also { ReflectionTestUtils.setField(it, "id", 10L) }
        }

        val response = reviewService.saveReview(
            USER_ID, USER_GAME_ID, ReviewSaveRequest(BigDecimal("4.5"), "새 리뷰", false),
        )

        assertThat(response.userGameId).isEqualTo(USER_GAME_ID)
        assertThat(response.rating).isEqualByComparingTo("4.5")
        assertThat(response.content).isEqualTo("새 리뷰")
        verify(reviewRepository).save(anyReview())
    }

    @DisplayName("이미 리뷰가 있으면 새로 생성하지 않고 기존 리뷰를 수정한다")
    @Test
    fun saveReviewUpdatesExistingReview() {
        prepareOwnedUserGame()
        val existingReview = prepareReview()
        `when`(existingReview.isActive).thenReturn(true)
        `when`(existingReview.version).thenReturn(2L)
        `when`(userGameRepository.findById(USER_GAME_ID)).thenReturn(Optional.of(userGame))
        `when`(reviewRepository.findIncludingDeletedByUserGameId(USER_GAME_ID))
            .thenReturn(existingReview)

        reviewService.saveReview(
            USER_ID, USER_GAME_ID, ReviewSaveRequest(2L, BigDecimal("3.5"), "수정된 리뷰", true),
        )

        verify(existingReview).edit(BigDecimal("3.5"), "수정된 리뷰", true)
        verify(reviewRepository).flush()
        verify(reviewRepository, never()).save(anyReview())
    }

    @DisplayName("사용자가 삭제했던 리뷰를 다시 작성하면 기존 리뷰를 복구한다")
    @Test
    fun restoreDeletedReview() {
        prepareOwnedUserGame()
        val deletedReview = prepareReview()
        `when`(deletedReview.isDeletedByUser).thenReturn(true)
        `when`(userGameRepository.findById(USER_GAME_ID)).thenReturn(Optional.of(userGame))
        `when`(reviewRepository.findIncludingDeletedByUserGameId(USER_GAME_ID))
            .thenReturn(deletedReview)

        reviewService.saveReview(
            USER_ID, USER_GAME_ID, ReviewSaveRequest(BigDecimal("4.0"), "다시 작성한 리뷰", false),
        )

        verify(deletedReview).restore(BigDecimal("4.0"), "다시 작성한 리뷰", false)
        verify(reviewRepository).flush()
        verify(reviewRepository, never()).save(anyReview())
    }

    @DisplayName("다른 사용자의 게임 기록에는 리뷰를 작성할 수 없다")
    @Test
    fun rejectReviewForOtherUsersGameRecord() {
        `when`(userGame.user).thenReturn(user)
        `when`(user.id).thenReturn(2L)
        `when`(userGameRepository.findById(USER_GAME_ID)).thenReturn(Optional.of(userGame))

        assertThatThrownBy {
            reviewService.saveReview(USER_ID, USER_GAME_ID, ReviewSaveRequest(BigDecimal("4.0"), null, false))
        }.isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("403-1")
            }

        verify(reviewRepository, never()).save(anyReview())
    }

    @DisplayName("존재하지 않는 게임 기록에 리뷰를 작성하면 404 오류가 발생한다")
    @Test
    fun rejectReviewWhenUserGameDoesNotExist() {
        `when`(userGameRepository.findById(USER_GAME_ID)).thenReturn(Optional.empty())

        assertThatThrownBy {
            reviewService.saveReview(USER_ID, USER_GAME_ID, ReviewSaveRequest(BigDecimal("4.0"), null, false))
        }.isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("404-2")
            }
    }

    @DisplayName("리뷰를 단건 조회한다")
    @Test
    fun getReview() {
        prepareOwnedUserGame()
        val review = prepareReview()
        `when`(review.id).thenReturn(10L)
        `when`(review.content).thenReturn("조회할 리뷰")
        `when`(reviewRepository.findActiveById(10L)).thenReturn(review)

        val response = reviewService.getReview(10L)

        assertThat(response.reviewId).isEqualTo(10L)
        assertThat(response.content).isEqualTo("조회할 리뷰")
    }

    @DisplayName("존재하지 않는 리뷰를 조회하면 404 오류가 발생한다")
    @Test
    fun getMissingReview() {
        `when`(reviewRepository.findActiveById(10L)).thenReturn(null)

        assertThatThrownBy { reviewService.getReview(10L) }
            .isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("404-3")
            }
    }

    @DisplayName("본인의 리뷰를 수정한다")
    @Test
    fun updateOwnReview() {
        prepareOwnedUserGame()
        val review = prepareReview()
        `when`(review.version).thenReturn(4L)
        `when`(reviewRepository.findActiveById(10L)).thenReturn(review)

        reviewService.updateReview(USER_ID, 10L, ReviewSaveRequest(4L, null, "글만 남긴 리뷰", true))

        verify(review).edit(null, "글만 남긴 리뷰", true)
        verify(reviewRepository).flush()
    }

    @DisplayName("다른 사용자의 리뷰는 수정할 수 없다")
    @Test
    fun rejectUpdateForOtherUsersReview() {
        `when`(userGame.user).thenReturn(user)
        `when`(user.id).thenReturn(2L)
        val review = prepareReview()
        `when`(reviewRepository.findActiveById(10L)).thenReturn(review)

        assertThatThrownBy {
            reviewService.updateReview(USER_ID, 10L, ReviewSaveRequest(BigDecimal("3.0"), null, false))
        }.isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("403-1")
            }

        verify(review, never()).edit(any(), any(), anyBoolean())
        verify(reviewRepository, never()).flush()
    }

    @DisplayName("본인의 리뷰를 삭제한다")
    @Test
    fun deleteOwnReview() {
        // 변경 원천을 기록할 리뷰의 기준 게임을 준비한다.
        `when`(userGame.game).thenReturn(game)
        `when`(game.id).thenReturn(GAME_ID)
        `when`(userGame.user).thenReturn(user)
        `when`(user.id).thenReturn(USER_ID)
        val review = prepareReview()
        `when`(review.version).thenReturn(1L)
        `when`(reviewRepository.findActiveForUpdate(10L)).thenReturn(review)

        reviewService.deleteReview(USER_ID, 10L, 1L)

        verify(reviewCommentService).purge(10L)
        verify(review).deleteByUser()
        verify(reviewRepository).flush()
        verify(reviewRepository, never()).delete(anyReview())
    }

    @DisplayName("리뷰 소프트 삭제는 신고 확인에 필요한 원문을 보존한다")
    @Test
    fun softDeleteKeepsReviewContent() {
        val review = Review(userGame, BigDecimal("4.5"), "신고 검토에 필요한 리뷰 원문", true)

        review.deleteByUser()

        assertThat(review.status).isEqualTo(ReviewStatus.DELETED_BY_USER)
        assertThat(review.deletedAt).isNotNull()
        assertThat(review.rating).isEqualByComparingTo("4.5")
        assertThat(review.content).isEqualTo("신고 검토에 필요한 리뷰 원문")
        assertThat(review.isSpoiler).isTrue()
    }

    @DisplayName("사용자가 삭제한 리뷰를 다시 작성하면 활성 상태로 복구한다")
    @Test
    fun restoreSoftDeletedReviewEntity() {
        val review = Review(userGame, null, "삭제 전 리뷰", false)
        review.deleteByUser()

        review.restore(BigDecimal("3.5"), "복구한 리뷰", true)

        assertThat(review.status).isEqualTo(ReviewStatus.ACTIVE)
        assertThat(review.deletedAt).isNull()
        assertThat(review.rating).isEqualByComparingTo("3.5")
        assertThat(review.content).isEqualTo("복구한 리뷰")
    }

    @DisplayName("다른 사용자의 리뷰는 삭제할 수 없다")
    @Test
    fun rejectDeleteForOtherUsersReview() {
        `when`(userGame.user).thenReturn(user)
        `when`(user.id).thenReturn(2L)
        val review = prepareReview()
        `when`(reviewRepository.findActiveForUpdate(10L)).thenReturn(review)

        assertThatThrownBy { reviewService.deleteReview(USER_ID, 10L, 0L) }
            .isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("403-1")
            }

        verify(review, never()).deleteByUser()
        verify(reviewRepository, never()).delete(anyReview())
    }

    @DisplayName("오래된 버전으로 리뷰를 수정하면 충돌 오류가 발생한다")
    @Test
    fun rejectStaleReviewUpdate() {
        prepareOwnedUserGame()
        val review = prepareReview()
        `when`(review.version).thenReturn(5L)
        `when`(reviewRepository.findActiveById(10L)).thenReturn(review)

        assertThatThrownBy {
            reviewService.updateReview(USER_ID, 10L, ReviewSaveRequest(4L, BigDecimal("3.0"), "오래된 내용", false))
        }.isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("409-6")
            }

        verify(review, never()).edit(any(), any(), anyBoolean())
        verify(reviewRepository, never()).flush()
    }

    @DisplayName("버전 없이 기존 리뷰를 저장하면 충돌 오류가 발생한다")
    @Test
    fun rejectExistingReviewSaveWithoutVersion() {
        prepareOwnedUserGame()
        val review = mock(Review::class.java)
        `when`(review.isActive).thenReturn(true)
        `when`(reviewRepository.findIncludingDeletedByUserGameId(USER_GAME_ID)).thenReturn(review)
        `when`(userGameRepository.findById(USER_GAME_ID)).thenReturn(Optional.of(userGame))

        assertThatThrownBy {
            reviewService.saveReview(USER_ID, USER_GAME_ID, ReviewSaveRequest(BigDecimal("4.0"), "버전 없는 수정", false))
        }.isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("409-6")
            }

        verify(review, never()).edit(any(), any(), anyBoolean())
    }

    @DisplayName("오래된 버전으로 리뷰를 삭제하면 충돌 오류가 발생한다")
    @Test
    fun rejectStaleReviewDelete() {
        prepareOwnedUserGame()
        val review = prepareReview()
        `when`(review.version).thenReturn(3L)
        `when`(reviewRepository.findActiveForUpdate(10L)).thenReturn(review)

        assertThatThrownBy { reviewService.deleteReview(USER_ID, 10L, 2L) }
            .isInstanceOf(ServiceException::class.java)
            .isInstanceOfSatisfying(ServiceException::class.java) { exception ->
                assertThat(exception.resultCode).isEqualTo("409-6")
            }

        verify(reviewCommentService, never()).purge(anyLong())
        verify(review, never()).deleteByUser()
        verify(reviewRepository, never()).flush()
    }

    @DisplayName("게임별 리뷰 목록을 페이지 정보와 함께 조회한다")
    @Test
    fun getGameReviews() {
        prepareOwnedUserGame()
        val review = prepareReview()
        `when`(review.id).thenReturn(10L)
        `when`(review.content).thenReturn("게임 리뷰")
        val pageable = PageRequest.of(1, 2)
        `when`(reviewRepository.findByUserGame_Game_Id(GAME_ID, pageable))
            .thenReturn(PageImpl(listOf(review), pageable, 5L))
        `when`(reviewLikeRepository.findLikeCountsByReviewIds(listOf(10L))).thenReturn(emptyList())
        `when`(reviewLikeRepository.countVisibleLikesByGameId(GAME_ID)).thenReturn(7L)

        val response = reviewService.getGameReviews(GAME_ID, pageable)

        assertThat(response.reviews).hasSize(1)
        assertThat(response.page).isEqualTo(1)
        assertThat(response.size).isEqualTo(2)
        assertThat(response.totalElements).isEqualTo(5L)
        assertThat(response.totalPages).isEqualTo(3)
        assertThat(response.hasNext).isTrue()
        assertThat(response.totalLikes).isEqualTo(7L)
    }

    @DisplayName("사용자별 리뷰 목록을 페이지 정보와 함께 조회한다")
    @Test
    fun getUserReviews() {
        prepareOwnedUserGame()
        val review = prepareReview()
        `when`(review.id).thenReturn(10L)
        `when`(review.content).thenReturn("사용자 리뷰")
        val pageable = PageRequest.of(0, 20)
        `when`(reviewRepository.findByUserGame_User_Id(USER_ID, pageable))
            .thenReturn(PageImpl(listOf(review), pageable, 1L))
        val likeCount = mock(ReviewLikeCountProjection::class.java)
        `when`(likeCount.reviewId).thenReturn(10L)
        `when`(likeCount.likeCount).thenReturn(3L)
        `when`(reviewLikeRepository.findLikeCountsByReviewIds(listOf(10L))).thenReturn(listOf(likeCount))
        `when`(reviewLikeRepository.countVisibleLikesByUserId(USER_ID)).thenReturn(3L)

        val response = reviewService.getUserReviews(USER_ID, pageable)

        assertThat(response.reviews.map { it.reviewId }).containsExactly(10L)
        assertThat(response.page).isZero()
        assertThat(response.totalElements).isEqualTo(1L)
        assertThat(response.reviews.first().likeCount).isEqualTo(3L)
        assertThat(response.totalLikes).isEqualTo(3L)
        assertThat(response.hasNext).isFalse()
    }

    @DisplayName("게임 기록이 없으면 내 상세 조회 결과가 모두 null이다")
    @Test
    fun getMyDetailedReviewWithoutUserGame() {
        `when`(userGameRepository.findByUser_IdAndGame_Id(USER_ID, GAME_ID)).thenReturn(null)

        val response = reviewService.getMyDetailedReview(USER_ID, GAME_ID)

        assertThat(response.userGame).isNull()
        assertThat(response.review).isNull()
        verify(reviewRepository, never()).findByUserGame_Id(anyLong())
    }

    @DisplayName("게임 기록만 있으면 내 상세 조회의 리뷰는 null이다")
    @Test
    fun getMyDetailedReviewWithOnlyUserGame() {
        prepareOwnedUserGame()
        `when`(userGame.game).thenReturn(game)
        `when`(game.id).thenReturn(GAME_ID)
        `when`(userGameRepository.findByUser_IdAndGame_Id(USER_ID, GAME_ID)).thenReturn(userGame)
        `when`(reviewRepository.findByUserGame_Id(USER_GAME_ID)).thenReturn(null)

        val response = reviewService.getMyDetailedReview(USER_ID, GAME_ID)

        assertThat(response.userGame).isNotNull()
        assertThat(requireNotNull(response.userGame).id).isEqualTo(USER_GAME_ID)
        assertThat(response.review).isNull()
    }

    @DisplayName("게임 기록과 리뷰가 모두 있으면 내 상세 조회에서 함께 반환한다")
    @Test
    fun getMyDetailedReviewWithReview() {
        prepareOwnedUserGame()
        `when`(userGame.game).thenReturn(game)
        `when`(game.id).thenReturn(GAME_ID)
        val review = prepareReview()
        `when`(review.id).thenReturn(10L)
        `when`(review.content).thenReturn("내 상세 리뷰")
        `when`(userGameRepository.findByUser_IdAndGame_Id(USER_ID, GAME_ID)).thenReturn(userGame)
        `when`(reviewRepository.findByUserGame_Id(USER_GAME_ID)).thenReturn(review)

        val response = reviewService.getMyDetailedReview(USER_ID, GAME_ID)

        assertThat(requireNotNull(response.userGame).id).isEqualTo(USER_GAME_ID)
        val responseReview = requireNotNull(response.review)
        assertThat(responseReview.reviewId).isEqualTo(10L)
        assertThat(responseReview.content).isEqualTo("내 상세 리뷰")
    }

    @ParameterizedTest
    @ValueSource(strings = ["game", "user"])
    @DisplayName("잘못된 정렬 필드는 리뷰·좋아요 조회 전에 거절한다")
    fun rejectsUnsupportedSortBeforeQuery(target: String) {
        val pageable = PageRequest.of(0, 20, Sort.by("missingField"))

        assertThatThrownBy {
            if (target == "game") reviewService.getGameReviews(GAME_ID, pageable)
            else reviewService.getUserReviews(USER_ID, pageable)
        }.isInstanceOfSatisfying(ServiceException::class.java) {
            assertThat(it.resultCode).isEqualTo("400-0")
        }

        verifyNoInteractions(reviewRepository, reviewLikeRepository)
    }

    private fun prepareOwnedUserGame() {
        lenient().`when`(userGame.id).thenReturn(USER_GAME_ID)
        `when`(userGame.user).thenReturn(user)
        `when`(user.id).thenReturn(USER_ID)
        lenient().`when`(userGame.game).thenReturn(game)
        lenient().`when`(game.id).thenReturn(GAME_ID)
        lenient().`when`(game.title).thenReturn("테스트 게임")
    }

    private fun prepareReview(): Review = mock(Review::class.java).also { review ->
        lenient().`when`(review.id).thenReturn(10L)
        `when`(review.userGame).thenReturn(userGame)
    }

    // Java matcher의 null 반환값과 Kotlin JPA Repository의 non-null 타입을 연결한다.
    private fun anyReview(): Review = any(Review::class.java) ?: Review(userGame, null, "매처용 리뷰", false)

    companion object {
        private const val USER_ID = 1L
        private const val GAME_ID = 6L
        private const val USER_GAME_ID = 100L
    }
}
