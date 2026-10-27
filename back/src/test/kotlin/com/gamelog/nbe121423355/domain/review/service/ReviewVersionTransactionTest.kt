package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.dto.request.DetailedReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewSaveRequest
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@MySqlIntegrationTest
class ReviewVersionTransactionTest @Autowired constructor(
    private val service: GameRecordReviewService,
    private val reviewService: ReviewService,
    private val reviews: ReviewRepository,
    private val records: UserGameRepository,
    private val games: GameRepository,
    private val users: UserRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)
    private var fixture: Fixture? = null

    // 테스트 전체를 하나의 트랜잭션으로 감싸지 않고 실제 커밋·롤백 결과를 조회한다.
    @BeforeEach
    fun setUp() {
        fixture = inTransaction {
            val user = users.save(User("review-version", "${UUID.randomUUID()}@test.com", "password"))
            val game = games.save(Game.createFromIgdb(IgdbGameResponse(
                igdbIds.incrementAndGet(), "review-version", "test", null, null, null,
                mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
            )))
            val userId = checkNotNull(user.id)
            val gameId = checkNotNull(game.id)
            val response = service.saveDetailedReview(
                userId, gameId,
                DetailedReviewSaveRequest(record("10.0"), ReviewSaveRequest(BigDecimal("4.0"), "기존 리뷰", false)),
            )
            val review = checkNotNull(response.review)
            Fixture(userId, gameId, checkNotNull(response.userGame?.id),
                checkNotNull(review.reviewId), checkNotNull(review.version))
        }
    }

    @AfterEach
    fun cleanUpCommittedFixtures() {
        fixture?.let { f ->
            inTransaction {
                reviews.deleteById(f.reviewId)
                records.deleteById(f.recordId)
                games.deleteById(f.gameId)
                users.deleteById(f.userId)
            }
        }
    }

    @Test
    @DisplayName("같은 버전을 읽은 두 트랜잭션이 수정하면 하나만 커밋된다")
    fun concurrentUpdatesAllowOnlyOneCommit() {
        val f = checkNotNull(fixture)
        val bothLoaded = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        val outcomes = try {
            val futures = listOf("A 화면 수정", "B 화면 수정").map { content ->
                executor.submit(Callable {
                    try {
                        inTransaction {
                            val version = checkNotNull(reviews.findById(f.reviewId).orElseThrow().version)
                            check(version == f.version)
                            // 두 트랜잭션이 동일 버전을 읽기 전에는 어느 쪽도 수정하지 않는다.
                            bothLoaded.await(10, TimeUnit.SECONDS)
                            reviewService.updateReview(f.userId, f.reviewId,
                                ReviewSaveRequest(version, BigDecimal("4.5"), content, false))
                        }
                        Outcome(content, null)
                    } catch (failure: Exception) {
                        Outcome(content, failure)
                    }
                })
            }
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }

        val successful = outcomes.filter { it.failure == null }
        val failed = outcomes.filter { it.failure != null }
        assertThat(successful).hasSize(1)
        assertThat(failed).hasSize(1)
        assertThat(failed.single().failure).isInstanceOf(ObjectOptimisticLockingFailureException::class.java)

        val saved = snapshot(f)
        assertThat(saved.content).isEqualTo(successful.single().content)
        assertThat(saved.version).isEqualTo(f.version + 1)
    }

    @Test
    @DisplayName("삭제 전 버전의 재작성은 거절하고 버전을 생략한 정상 재작성은 허용한다")
    fun staleSaveCannotRestoreDeletedReview() {
        val f = checkNotNull(fixture)
        service.deleteReview(f.userId, f.reviewId, f.version)
        val deleted = snapshot(f)
        assertThat(deleted.status).isEqualTo(ReviewStatus.DELETED_BY_USER)
        assertThat(deleted.version).isGreaterThan(f.version)

        assertThatThrownBy {
            service.saveReview(f.userId, f.recordId,
                ReviewSaveRequest(f.version, BigDecimal("2.0"), "삭제 전 화면의 내용", false))
        }.isInstanceOfSatisfying(ServiceException::class.java) {
            assertThat(it.resultCode).isEqualTo("409-6")
        }
        assertThat(snapshot(f)).isEqualTo(deleted)

        val restored = service.saveReview(f.userId, f.recordId,
            ReviewSaveRequest(BigDecimal("5.0"), "새로 작성한 리뷰", false))
        assertThat(restored.reviewId).isEqualTo(f.reviewId)
        val saved = snapshot(f)
        assertThat(saved.status).isEqualTo(ReviewStatus.ACTIVE)
        assertThat(saved.content).isEqualTo("새로 작성한 리뷰")
        assertThat(saved.version).isGreaterThan(deleted.version)
        assertThat(saved.inLibrary).isTrue()
    }

    @Test
    @DisplayName("리뷰 버전 충돌 시 함께 변경한 플레이 시간과 상태도 롤백된다")
    fun staleReviewRollsBackGameRecordChanges() {
        val f = checkNotNull(fixture)
        service.updateReview(f.userId, f.reviewId,
            ReviewSaveRequest(f.version, BigDecimal("5.0"), "다른 화면의 최신 리뷰", false))
        val before = snapshot(f)

        assertThatThrownBy {
            service.saveDetailedReview(f.userId, f.gameId,
                DetailedReviewSaveRequest(record("99.0", wishlist = true),
                    ReviewSaveRequest(f.version, BigDecimal("2.0"), "오래된 리뷰", false)))
        }.isInstanceOfSatisfying(ServiceException::class.java) {
            assertThat(it.resultCode).isEqualTo("409-6")
        }

        val after = snapshot(f)
        assertThat(after).isEqualTo(before)
        assertThat(after.playTime).isEqualByComparingTo("10.0")
        assertThat(after.wishlist).isFalse()
        assertThat(after.content).isEqualTo("다른 화면의 최신 리뷰")
    }

    private fun snapshot(f: Fixture): Snapshot = inTransaction {
        val review = reviews.findById(f.reviewId).orElseThrow()
        val record = records.findById(f.recordId).orElseThrow()
        Snapshot(checkNotNull(review.version), review.status, review.content, review.rating,
            record.playTimeHours, record.isWishlist, record.isInLibrary)
    }

    private fun record(hours: String, wishlist: Boolean = false) = UserGameReqBody(
        null, false, false, wishlist, false, null, BigDecimal(hours), null, null, null, null, null,
    )

    private fun <T : Any> inTransaction(block: () -> T): T =
        checkNotNull(transactions.execute { block() })

    private data class Fixture(val userId: Long, val gameId: Long, val recordId: Long,
        val reviewId: Long, val version: Long)
    private data class Outcome(val content: String, val failure: Exception?)
    private data class Snapshot(val version: Long, val status: ReviewStatus?, val content: String?,
        val rating: BigDecimal?, val playTime: BigDecimal?, val wishlist: Boolean, val inLibrary: Boolean)

    companion object {
        private val igdbIds = AtomicLong(1_980_000)
    }
}
