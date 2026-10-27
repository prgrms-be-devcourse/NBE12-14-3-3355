package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.response.ReviewResponse
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameSaveResult
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class GameRecordReviewServiceTest {
    @Mock private lateinit var userGameService: UserGameService
    @Mock private lateinit var reviewService: ReviewService
    @Mock private lateinit var userGame: UserGame
    @Mock private lateinit var user: User
    @Mock private lateinit var game: Game
    @InjectMocks private lateinit var service: GameRecordReviewService

    private lateinit var userGameRequest: UserGameReqBody

    @BeforeEach
    fun setUp() {
        userGameRequest = UserGameReqBody(
            playStatus = null,
            isPlaying = false,
            isBacklog = true,
            isWishlist = false,
            isLiked = false,
            platformId = null,
            playTimeHours = null,
            finishTimeHours = null,
            masterTimeHours = null,
            startedAt = null,
            completedAt = null,
            lastPlayedAt = null,
        )
        lenient().`when`(userGame.id).thenReturn(USER_GAME_ID)
        lenient().`when`(userGame.user).thenReturn(user)
        lenient().`when`(user.id).thenReturn(USER_ID)
        lenient().`when`(userGame.game).thenReturn(game)
        lenient().`when`(game.id).thenReturn(GAME_ID)
        lenient().`when`(userGame.isBacklog).thenReturn(true)
        lenient().`when`(userGameService.prepareGameRecordForReview(USER_ID, GAME_ID, userGameRequest))
            .thenReturn(UserGameSaveResult(userGame, true))
    }

    @Test
    @DisplayName("리뷰가 없으면 게임 기록만 저장하고 기존 리뷰를 조회한다")
    fun saveGameRecordWithoutReview() {
        val existing = mock(ReviewResponse::class.java)
        `when`(reviewService.getReviewByUserGameId(USER_GAME_ID)).thenReturn(existing)

        val response = service.saveDetailedReview(
            USER_ID, GAME_ID, DetailedReviewSaveRequest(userGameRequest, null),
        )

        assertThat(requireNotNull(response.userGame).backlog).isTrue()
        assertThat(response.review).isSameAs(existing)
        verify(userGameService).synchronizeLibraryState(userGame)
        verify(reviewService, never()).saveReview(anyLong(), anyLong(), anyReviewRequest())
    }

    @Test
    @DisplayName("빈 리뷰 객체는 기존 리뷰를 덮어쓰지 않는다")
    fun preserveExistingReviewForEmptyRequest() {
        val existing = mock(ReviewResponse::class.java)
        `when`(reviewService.getReviewByUserGameId(USER_GAME_ID)).thenReturn(existing)

        val response = service.saveDetailedReview(
            USER_ID, GAME_ID,
            DetailedReviewSaveRequest(userGameRequest, ReviewSaveRequest(null, "   ", false)),
        )

        assertThat(response.review).isSameAs(existing)
        verify(reviewService, never()).saveReview(anyLong(), anyLong(), anyReviewRequest())
    }

    @Test
    @DisplayName("리뷰가 있으면 저장된 게임 기록 ID로 리뷰 저장을 위임한다")
    fun saveGameRecordAndReview() {
        val request = ReviewSaveRequest(BigDecimal("4.5"), "리뷰", false)
        val savedReview = mock(ReviewResponse::class.java)
        `when`(reviewService.saveReview(USER_ID, USER_GAME_ID, request)).thenReturn(savedReview)

        val response = service.saveDetailedReview(
            USER_ID, GAME_ID, DetailedReviewSaveRequest(userGameRequest, request),
        )

        assertThat(response.review).isSameAs(savedReview)
        verify(reviewService).saveReview(USER_ID, USER_GAME_ID, request)
        verify(reviewService, never()).getReviewByUserGameId(USER_GAME_ID)
    }

    @Test
    @DisplayName("리뷰 저장 실패를 삼키지 않고 상위 트랜잭션으로 전파한다")
    fun propagateReviewFailure() {
        val request = ReviewSaveRequest(BigDecimal("4.5"), "리뷰", false)
        val failure = RuntimeException("리뷰 저장 실패")
        `when`(reviewService.saveReview(USER_ID, USER_GAME_ID, request)).thenThrow(failure)

        assertThatThrownBy {
            service.saveDetailedReview(USER_ID, GAME_ID, DetailedReviewSaveRequest(userGameRequest, request))
        }.isSameAs(failure)
    }

    @Test
    @DisplayName("리뷰 단독 저장 후 라이브러리 상태를 동기화한다")
    fun saveReviewAndSynchronizeLibrary() {
        val request = ReviewSaveRequest(BigDecimal("4.0"), null, false)
        val savedReview = mock(ReviewResponse::class.java)
        `when`(savedReview.userGameId).thenReturn(USER_GAME_ID)
        `when`(reviewService.saveReview(USER_ID, USER_GAME_ID, request)).thenReturn(savedReview)

        val response = service.saveReview(USER_ID, USER_GAME_ID, request)

        assertThat(response).isSameAs(savedReview)
        verify(userGameService).synchronizeLibraryState(USER_GAME_ID)
    }

    @Test
    @DisplayName("리뷰 수정 후 라이브러리 상태를 동기화한다")
    fun updateReviewAndSynchronizeLibrary() {
        val reviewId = 10L
        val request = ReviewSaveRequest(null, "본문만 남김", false)
        val updatedReview = mock(ReviewResponse::class.java)
        `when`(updatedReview.userGameId).thenReturn(USER_GAME_ID)
        `when`(reviewService.updateReview(USER_ID, reviewId, request)).thenReturn(updatedReview)

        val response = service.updateReview(USER_ID, reviewId, request)

        assertThat(response).isSameAs(updatedReview)
        verify(userGameService).synchronizeLibraryState(USER_GAME_ID)
    }

    @Test
    @DisplayName("리뷰 삭제 후 라이브러리 상태를 동기화한다")
    fun deleteReviewAndSynchronizeLibrary() {
        val reviewId = 10L
        val version = 3L
        `when`(reviewService.deleteReview(USER_ID, reviewId, version)).thenReturn(USER_GAME_ID)

        service.deleteReview(USER_ID, reviewId, version)

        verify(userGameService).synchronizeLibraryState(USER_GAME_ID)
    }

    // Mockito의 Java matcher가 반환하는 null을 Kotlin의 non-null 매개변수에 전달하지 않는다.
    private fun anyReviewRequest(): ReviewSaveRequest =
        any<ReviewSaveRequest>() ?: ReviewSaveRequest(null, null, false)

    companion object {
        private const val USER_ID = 1L
        private const val GAME_ID = 6L
        private const val USER_GAME_ID = 100L
    }
}
