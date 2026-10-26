package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameInputChangeRepository
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.service.GameRecordReviewService
import com.gamelog.nbe121423355.domain.review.service.ReviewService
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class RecommendationChangeLifecycleTest {

    @Autowired
    private lateinit var gameRecordReviewService: GameRecordReviewService

    @Autowired
    private lateinit var reviewService: ReviewService

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var games: GameRepository

    @Autowired
    private lateinit var changes: RelatedGameInputChangeRepository

    @Test
    fun actualRecordAndReviewLifecycleKeepChangeSourcesPending() {
        // given: 실제 상세 저장 경로로 좋아요와 리뷰를 함께 기록한다.
        val userId = requireNotNull(users.save(User("review-library", "review-library@example.com", "password")).id)
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = 990001L, name = "review-library", summary = "test", cover = null,
            first_release_date = null, rating = null,
            involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        val gameId = requireNotNull(games.save(game).id)
        val saved = gameRecordReviewService.saveDetailedReview(
            userId, gameId, DetailedReviewSaveRequest(record(), rating()),
        )
        val initial = requireNotNull(changes.findByGameId(gameId))
        changes.markExpanded(gameId, initial.changeVersion)

        // when: 리뷰 삭제·복원 후 탈퇴의 일괄 숨김 저장 경로를 실행한다.
        val review = requireNotNull(saved.review)
        gameRecordReviewService.deleteReview(userId, requireNotNull(review.reviewId), review.version)
        val afterDeletion = requireNotNull(changes.findByGameId(gameId))
        gameRecordReviewService.saveReview(userId, requireNotNull(requireNotNull(saved.userGame).id), rating())
        val beforeWithdrawal = requireNotNull(changes.findByGameId(gameId)).changeVersion
        reviewService.hideAllByWithdrawnUser(userId)

        // then: 각 실제 저장에서 새 원천 버전을 기록하고 일괄 숨김도 리뷰 변경으로 남긴다.
        val remaining = requireNotNull(changes.findByGameId(gameId))
        assertThat(initial.reasonMask and RelatedGameChangeReason.USER_GAME_RECORD.mask).isNotZero()
        assertThat(afterDeletion.changeVersion).isGreaterThan(initial.changeVersion)
        assertThat(remaining.changeVersion).isGreaterThan(beforeWithdrawal)
        assertThat(remaining.reasonMask and RelatedGameChangeReason.REVIEW.mask).isNotZero()
        assertThat(remaining.isPending).isTrue()
    }

    private fun record() = UserGameReqBody(
        playStatus = null, isPlaying = false, isBacklog = false, isWishlist = false, isLiked = true,
        platformId = null, playTimeHours = null, finishTimeHours = null, masterTimeHours = null,
        startedAt = null, completedAt = null, lastPlayedAt = null,
    )

    private fun rating() = ReviewSaveRequest(BigDecimal("4.0"), null, false)
}
