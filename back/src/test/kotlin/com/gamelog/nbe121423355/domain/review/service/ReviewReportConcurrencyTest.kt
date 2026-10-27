package com.gamelog.nbe121423355.domain.review.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportStatusUpdateRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportCreateRequest
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.EnumSource
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
                reports.deleteAll(reports.findAllByReviewIdsAndStatus(listOf(f.reviewId), null))
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

    @ParameterizedTest
    @EnumSource(value = ReportStatus::class, names = ["APPROVED", "REJECTED"])
    @DisplayName("공개 리뷰를 미리 읽었어도 관리자 처리 후 숨김 상태면 신규 신고를 거절하고 반려 후에는 허용한다")
    fun staleIntakeRechecksReviewAfterModeration(decision: ReportStatus) {
        val f = requireNotNull(fixture)
        val reporterId = newReporter()
        val reviewRead = CountDownLatch(1)
        val moderationFinished = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val outcome = try {
            val intake = executor.submit(Callable {
                try {
                    val reportId = inTransaction {
                        assertThat(reviews.findActiveById(f.reviewId)).isPresent()
                        reviewRead.countDown()
                        check(moderationFinished.await(15, TimeUnit.SECONDS))
                        requireNotNull(service.createReport(reporterId, f.reviewId,
                            ReviewReportCreateRequest("동시 접수")).reportId)
                    }
                    IntakeOutcome(reportId, null)
                } catch (failure: Exception) {
                    IntakeOutcome(null, failure)
                }
            })
            try {
                check(reviewRead.await(10, TimeUnit.SECONDS))
                service.updateStatus(f.reportIds.first(), ReviewReportStatusUpdateRequest(decision))
            } finally {
                moderationFinished.countDown()
            }
            intake.get(30, TimeUnit.SECONDS)
        } finally {
            moderationFinished.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }

        if (decision == ReportStatus.APPROVED) {
            assertThat(outcome.failure).isInstanceOfSatisfying(ServiceException::class.java) {
                assertThat(it.resultCode).isEqualTo("404-3")
            }
            inTransaction {
                assertThat(reports.existsByReview_IdAndReporter_Id(f.reviewId, reporterId)).isFalse()
            }
        } else {
            assertThat(outcome.failure).isNull()
            inTransaction {
                assertThat(reports.findById(requireNotNull(outcome.reportId)).orElseThrow().status)
                    .isEqualTo(ReportStatus.PENDING)
            }
        }
        assertSavedDecision(f, decision)
    }

    @Test
    @DisplayName("관리자 처리 전에 접수된 신고는 기존 대기 신고와 함께 승인된다")
    fun intakeCommittedFirstIsIncludedInModeration() {
        val f = requireNotNull(fixture)
        val report = service.createReport(newReporter(), f.reviewId, ReviewReportCreateRequest("먼저 접수"))

        service.updateStatus(f.reportIds.first(), ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))

        inTransaction {
            assertThat(reports.findById(requireNotNull(report.reportId)).orElseThrow().status)
                .isEqualTo(ReportStatus.APPROVED)
        }
        assertSavedDecision(f, ReportStatus.APPROVED)
    }

    @Test
    @DisplayName("신고 접수와 승인이 동시에 시작되어도 숨긴 리뷰에 미처리 신고가 남지 않는다")
    fun simultaneousIntakeAndApprovalLeaveNoPendingReportOnHiddenReview() {
        val f = requireNotNull(fixture)
        val reporterId = newReporter()
        val bothStarted = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        val intakeOutcome = try {
            val intake = executor.submit(Callable {
                try {
                    val reportId = inTransaction {
                        bothStarted.await(10, TimeUnit.SECONDS)
                        requireNotNull(service.createReport(reporterId, f.reviewId,
                            ReviewReportCreateRequest("동시 접수")).reportId)
                    }
                    IntakeOutcome(reportId, null)
                } catch (failure: Exception) {
                    IntakeOutcome(null, failure)
                }
            })
            val moderation = executor.submit(Callable {
                inTransaction {
                    bothStarted.await(10, TimeUnit.SECONDS)
                    service.updateStatus(f.reportIds.first(), ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))
                }
            })
            moderation.get(30, TimeUnit.SECONDS)
            intake.get(30, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }

        if (intakeOutcome.failure != null) {
            assertThat(intakeOutcome.failure).isInstanceOfSatisfying(ServiceException::class.java) {
                assertThat(it.resultCode).isEqualTo("404-3")
            }
        } else {
            inTransaction {
                assertThat(reports.findById(requireNotNull(intakeOutcome.reportId)).orElseThrow().status)
                    .isEqualTo(ReportStatus.APPROVED)
            }
        }
        inTransaction {
            assertThat(reports.findAllByReviewIdsAndStatus(listOf(f.reviewId), ReportStatus.PENDING)).isEmpty()
        }
        assertSavedDecision(f, ReportStatus.APPROVED)
    }

    private fun newReporter(): Long {
        val f = requireNotNull(fixture)
        val id = inTransaction {
            requireNotNull(users.save(User("new-reporter", "${UUID.randomUUID()}@test.com", "password")).id)
        }
        fixture = f.copy(reporterIds = f.reporterIds + id)
        return id
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
    private data class IntakeOutcome(val reportId: Long?, val failure: Exception?)

    companion object {
        private val igdbIds = AtomicLong(1_981_000)
    }
}
