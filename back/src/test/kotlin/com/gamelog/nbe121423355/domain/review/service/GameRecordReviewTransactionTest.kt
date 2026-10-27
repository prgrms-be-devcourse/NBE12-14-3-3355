package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@MySqlIntegrationTest
class GameRecordReviewTransactionTest @Autowired constructor(
    private val service: GameRecordReviewService,
    private val users: UserRepository,
    private val games: GameRepository,
    private val userGames: UserGameRepository,
) {
    private var user: User? = null
    private var game: Game? = null

    @AfterEach
    fun cleanUpCommittedFixtures() {
        // 실제 서비스 트랜잭션의 롤백을 검증하므로 테스트 트랜잭션 대신 직접 정리한다.
        game?.let { games.deleteById(requireNotNull(it.id)) }
        user?.let { users.deleteById(requireNotNull(it.id)) }
    }

    @Test
    @DisplayName("리뷰 저장이 실패하면 앞서 생성한 게임 기록도 롤백한다")
    fun rollbackGameRecordWhenReviewSaveFails() {
        val savedUser = users.save(User("transaction-user", "transaction-user@example.com", "password"))
            .also { user = it }
        val savedGame = games.save(Game.createFromIgdb(IgdbGameResponse(
            id = 998877L,
            name = "transaction-game",
            summary = "test",
            cover = null,
            first_release_date = null,
            rating = null,
            involved_companies = mutableListOf(),
            genres = mutableListOf(),
            platforms = mutableListOf(),
            collections = mutableListOf(),
        ))).also { game = it }
        val userId = requireNotNull(savedUser.id)
        val gameId = requireNotNull(savedGame.id)
        val gameRecord = UserGameReqBody(
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
        val invalidReview = ReviewSaveRequest(BigDecimal("4.3"), "잘못된 별점 단위", false)

        assertThatThrownBy {
            service.saveDetailedReview(userId, gameId, DetailedReviewSaveRequest(gameRecord, invalidReview))
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThat(userGames.findByUser_IdAndGame_Id(userId, gameId)).isNull()
    }
}
