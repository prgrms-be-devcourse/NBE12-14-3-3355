package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportStatusUpdateRequest
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewReportRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@MySqlIntegrationTest
class ReviewReportConcurrencyTest {
    @Autowired private lateinit var service: ReviewReportService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var games: GameRepository
    @Autowired private lateinit var records: UserGameRepository
    @Autowired private lateinit var reviews: ReviewRepository
    @Autowired private lateinit var reports: ReviewReportRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private var fixture: Fixture? = null

    @BeforeEach
    fun setUp() {
        fixture = inTransaction {
            val writer = users.save(User("report-writer", "${UUID.randomUUID()}@test.com", "password"))
            val reporters = (1..2).map { users.save(User("reporter-$it", "${UUID.randomUUID()}@test.com", "password")) }
            val game = games.save(Game.createFromIgdb(IgdbGameResponse(
                igdbIds.incrementAndGet(), "report-concurrency", "test", null, null, null,
                mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
            )))
            val record = records.save(UserGame(writer, game).apply { updateLibraryStatus(hasActiveReview = true) })
            val review = reviews.save(Review(record, BigDecimal("4.0"), "신고 대상 리뷰", false))
            val reportIds = reporters.map {
                requireNotNull(reports.save(ReviewReport(review, it, "신고 사유")).id)
            }
            Fixture(requireNotNull(writer.id), reporters.map { requireNotNull(it.id) },
                requireNotNull(game.id), requireNotNull(record.id), requireNotNull(review.id), reportIds)
        }
    }

    @AfterEach
    fun cleanUpCommittedFixtures() {
        fixture?.let { f ->
            inTransaction {
                jdbc.update("DELETE FROM notifications WHERE recipient_id = ?", f.writerId)
                reports.deleteAllById(f.reportIds)
                reviews.deleteById(f.reviewId)
                records.deleteById(f.recordId)
                games.deleteById(f.gameId)
                (f.reporterIds + f.writerId).forEach(users::deleteById)
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "APPROVED, REJECTED, true",
        "APPROVED, REJECTED, false",
        "REJECTED, APPROVED, true",
        "REJECTED, APPROVED, false",
    )
    @DisplayName("같은 리뷰의 대기 신고를 읽은 두 트랜잭션은 먼저 커밋한 결과를 덮어쓰지 않는다")
    fun staleDecisionCannotOverwriteCommittedDecision(
        firstStatus: ReportStatus,
        secondStatus: ReportStatus,
        sameReport: Boolean,
    ) {
        val f = requireNotNull(fixture)
        val bothLoaded = CyclicBarrier(2)
        val firstFinished = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val secondReportId = if (sameReport) f.reportIds.first() else f.reportIds.last()
        val outcomes = try {
            val first = executor.submit(Callable {
                try {
                    val status = inTransaction {
                        // 두 실제 DB 트랜잭션이 PENDING을 읽은 뒤 첫 결정을 커밋한다.
                        assertThat(reports.findById(f.reportIds.first()).orElseThrow().status)
                            .isEqualTo(ReportStatus.PENDING)
                        bothLoaded.await(10, TimeUnit.SECONDS)
                        requireNotNull(service.updateStatus(f.reportIds.first(),
                            ReviewReportStatusUpdateRequest(firstStatus)).status)
                    }
                    Outcome(status, null)
                } catch (failure: Exception) {
                    Outcome(null, failure)
                } finally {
                    firstFinished.countDown()
                }
            })
            val second = executor.submit(Callable {
                try {
                    val status = inTransaction {
                        assertThat(reports.findById(secondReportId).orElseThrow().status)
                            .isEqualTo(ReportStatus.PENDING)
                        bothLoaded.await(10, TimeUnit.SECONDS)
                        check(firstFinished.await(15, TimeUnit.SECONDS))
                        // MySQL REPEATABLE READ의 이전 스냅샷을 가진 채 반대 결정을 요청한다.
                        requireNotNull(service.updateStatus(secondReportId,
                            ReviewReportStatusUpdateRequest(secondStatus)).status)
                    }
                    Outcome(status, null)
                } catch (failure: Exception) {
                    Outcome(null, failure)
                }
            })
            listOf(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }

        assertThat(outcomes.first().failure).isNull()
        assertThat(outcomes.first().status).isEqualTo(firstStatus)
        assertThat(outcomes.last().failure).isInstanceOfSatisfying(ServiceException::class.java) {
            assertThat(it.resultCode).isEqualTo("409-4")
        }
        assertSavedDecision(f, firstStatus)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @DisplayName("동시에 승인·반려를 시작하면 같은 신고 ID와 다른 신고 ID 모두 한 요청만 성공한다")
    fun simultaneousDecisionsAllowOnlyOneCommit(sameReport: Boolean) {
        val f = requireNotNull(fixture)
        val bothStarted = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        val requests = listOf(
            f.reportIds.first() to ReportStatus.APPROVED,
            (if (sameReport) f.reportIds.first() else f.reportIds.last()) to ReportStatus.REJECTED,
        )
        val outcomes = try {
            val futures = requests.map { (reportId, target) ->
                executor.submit(Callable {
                    try {
                        val status = inTransaction {
                            bothStarted.await(10, TimeUnit.SECONDS)
                            requireNotNull(service.updateStatus(reportId, ReviewReportStatusUpdateRequest(target)).status)
                        }
                        Outcome(status, null)
                    } catch (failure: Exception) {
                        Outcome(null, failure)
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
        assertThat(failed.single().failure).isInstanceOfSatisfying(ServiceException::class.java) {
            assertThat(it.resultCode).isEqualTo("409-4")
        }
        assertSavedDecision(f, requireNotNull(successful.single().status))
    }

    private fun assertSavedDecision(f: Fixture, expected: ReportStatus) = inTransaction {
        assertThat(reports.findAllById(f.reportIds).map { it.status }).containsOnly(expected)
        val hidden = expected == ReportStatus.APPROVED
        assertThat(reviews.findById(f.reviewId).orElseThrow().status)
            .isEqualTo(if (hidden) ReviewStatus.HIDDEN_BY_ADMIN else ReviewStatus.ACTIVE)
        assertThat(records.findById(f.recordId).orElseThrow().isInLibrary).isEqualTo(!hidden)
    }

    private fun <T : Any> inTransaction(block: () -> T): T = requireNotNull(
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
        }.execute { block() },
    )

    private data class Fixture(val writerId: Long, val reporterIds: List<Long>, val gameId: Long,
        val recordId: Long, val reviewId: Long, val reportIds: List<Long>)
    private data class Outcome(val status: ReportStatus?, val failure: Exception?)

    companion object {
        private val igdbIds = AtomicLong(1_981_000)
    }
}
