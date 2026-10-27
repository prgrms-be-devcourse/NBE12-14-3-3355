package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.dto.WithdrawRequestDto
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.user.service.UserService
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@MySqlIntegrationTest
class ReviewWithdrawalConcurrencyTest {
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var games: GameRepository
    @Autowired private lateinit var records: UserGameRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var service: GameRecordReviewService
    @Autowired private lateinit var reviewService: ReviewService
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var passwords: PasswordEncoder
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    private var fixture: Fixture? = null

    @BeforeEach
    fun setUp() {
        fixture = inTransaction {
            val user = users.save(User("review-withdraw", "${UUID.randomUUID()}@test.com",
                requireNotNull(passwords.encode("password"))))
            val game = games.save(Game.createFromIgdb(IgdbGameResponse(
                igdbIds.incrementAndGet(), "review-withdraw", "test", null, null, null,
                mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
            )))
            val record = records.save(UserGame(user, game, playTimeHours = BigDecimal("10.0"))
                .apply { updateLibraryStatus(hasActiveReview = true) })
            val review = reviews.saveAndFlush(Review(record, BigDecimal("4.0"), "탈퇴 전 원문", false))
            Fixture(requireNotNull(user.id), requireNotNull(game.id), requireNotNull(record.id),
                requireNotNull(review.id), requireNotNull(review.version))
        }
    }

    @AfterEach
    fun cleanUpCommittedFixtures() {
        fixture?.let { f -> inTransaction {
            reviews.deleteById(f.reviewId)
            records.deleteById(f.recordId)
            games.deleteById(f.gameId)
            users.deleteById(f.userId)
        } }
    }

    @ParameterizedTest
    @ValueSource(strings = ["update", "save", "detailed"])
    @DisplayName("탈퇴 전 리뷰를 읽은 수정·저장·통합 저장은 탈퇴 후 커밋하지 못하고 함께 변경한 기록도 롤백된다")
    fun staleReviewWriteCannotRestoreWithdrawnReview(route: String) {
        val f = requireNotNull(fixture)
        val reviewLoaded = CountDownLatch(1)
        val withdrawalFinished = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val failure = try {
            val update = executor.submit(Callable {
                try {
                    inTransaction {
                        val loaded = reviews.findActiveById(f.reviewId).orElseThrow()
                        check(loaded.version == f.version)
                        reviewLoaded.countDown()
                        check(withdrawalFinished.await(15, TimeUnit.SECONDS))
                        val request = ReviewSaveRequest(f.version, BigDecimal("5.0"), "탈퇴 후 늦게 도착한 수정", false)
                        when (route) {
                            "update" -> service.updateReview(f.userId, f.reviewId, request)
                            "save" -> service.saveReview(f.userId, f.recordId, request)
                            "detailed" -> service.saveDetailedReview(f.userId, f.gameId,
                                DetailedReviewSaveRequest(record(), request))
                            else -> error("지원하지 않는 테스트 경로: $route")
                        }
                    }
                    null
                } catch (failure: Exception) {
                    failure
                }
            })
            try {
                check(reviewLoaded.await(10, TimeUnit.SECONDS))
                userService.withdraw(f.userId, WithdrawRequestDto("password"))
            } finally {
                withdrawalFinished.countDown()
            }
            update.get(30, TimeUnit.SECONDS)
        } finally {
            withdrawalFinished.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }

        assertThat(failure).isInstanceOf(ObjectOptimisticLockingFailureException::class.java)
        inTransaction {
            val saved = reviews.findById(f.reviewId).orElseThrow()
            assertThat(users.findById(f.userId).orElseThrow().isDeleted).isTrue()
            assertThat(saved.status).isEqualTo(ReviewStatus.DELETED_BY_USER)
            assertThat(saved.deletedAt).isNotNull()
            assertThat(saved.version).isEqualTo(f.version + 1)
            assertThat(saved.content).isEqualTo("탈퇴 전 원문")
            assertThat(saved.rating).isEqualByComparingTo("4.0")
            assertThat(reviews.findActiveById(f.reviewId)).isEmpty()
            assertThat(reviewService.getGameReviews(f.gameId, PageRequest.of(0, 20)).reviews).isEmpty()
            val record = records.findById(f.recordId).orElseThrow()
            assertThat(record.playTimeHours).isEqualByComparingTo("10.0")
            assertThat(record.isWishlist).isFalse()
            // 탈퇴 시 라이브러리 row·통계를 보존하는 기존 정책은 유지한다.
            assertThat(record.isInLibrary).isTrue()
        }
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus::class, names = ["ACTIVE", "DELETED_BY_USER", "HIDDEN_BY_ADMIN"])
    @DisplayName("탈퇴는 활성 리뷰의 상태·버전만 갱신하고 이미 삭제·관리자 숨김된 리뷰는 유지한다")
    fun withdrawalUpdatesOnlyActiveReviewVersions(initialStatus: ReviewStatus) {
        val f = requireNotNull(fixture)
        val beforeVersion = inTransaction {
            val review = reviews.findById(f.reviewId).orElseThrow()
            when (initialStatus) {
                ReviewStatus.DELETED_BY_USER -> review.deleteByUser()
                ReviewStatus.HIDDEN_BY_ADMIN -> review.hideByAdmin()
                else -> Unit
            }
            reviews.flush()
            requireNotNull(review.version)
        }

        userService.withdraw(f.userId, WithdrawRequestDto("password"))

        inTransaction {
            val saved = reviews.findById(f.reviewId).orElseThrow()
            val wasActive = initialStatus == ReviewStatus.ACTIVE
            assertThat(saved.status).isEqualTo(if (wasActive) ReviewStatus.DELETED_BY_USER else initialStatus)
            assertThat(saved.version).isEqualTo(beforeVersion + if (wasActive) 1 else 0)
            assertThat(saved.content).isEqualTo("탈퇴 전 원문")
            assertThat(saved.rating).isEqualByComparingTo("4.0")
        }
    }

    private fun record() = UserGameReqBody(
        playStatus = null, isPlaying = false, isBacklog = false, isWishlist = true, isLiked = false,
        platformId = null, playTimeHours = BigDecimal("99.0"), finishTimeHours = null, masterTimeHours = null,
        startedAt = null, completedAt = null, lastPlayedAt = null,
    )

    private fun <T : Any> inTransaction(block: () -> T): T = requireNotNull(
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
        }.execute { block() },
    )

    private data class Fixture(val userId: Long, val gameId: Long, val recordId: Long,
        val reviewId: Long, val version: Long)

    companion object {
        private val igdbIds = AtomicLong(1_986_000)
    }
}
