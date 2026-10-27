package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewReport
import com.gamelog.nbe121423355.domain.review.service.ReviewReportService
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class ReviewReportRepositoryTest {
    @Autowired private lateinit var reports: ReviewReportRepository
    @Autowired private lateinit var service: ReviewReportService
    @Autowired private lateinit var em: EntityManager

    private lateinit var game: Game
    private lateinit var reporters: List<User>
    private lateinit var groups: List<Group>

    @BeforeEach
    fun setUp() {
        game = Game.createFromIgdb(IgdbGameResponse(
            1_982_001L, "report-order", "test", null, null, null,
            mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
        ))
        em.persist(game)
        reporters = (1..2).map {
            User("order-reporter-$it", "order-reporter-$it@test.com", "password").also(em::persist)
        }
        val tiedAt = LocalDateTime.of(2026, 10, 20, 10, 0)
        // 최신 그룹의 ID를 가장 작게 만들어 신고 시각이 ID보다 우선하는지도 검증한다.
        groups = listOf(saveGroup(0, tiedAt.plusMinutes(1))) + (1..3).map { saveGroup(it, tiedAt) }
        em.flush()
        em.clear()
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = ReportStatus::class, names = ["PENDING", "REJECTED"])
    @DisplayName("신고 시각이 같은 그룹도 리뷰 ID 내림차순으로 페이지 간 중복·누락 없이 조회한다")
    fun pagesEqualDatesByReviewIdDescending(status: ReportStatus?) {
        val expected = (groups.take(1) + groups.drop(1).asReversed()).map { it.reviewId }
        val actual = mutableListOf<Long>()
        for (pageNumber in 0..2) {
            val page = PageRequest.of(pageNumber, 2)
            val expectedPage = expected.drop(pageNumber * 2).take(2)
            val first = reports.findReviewIdsGroupedByReview(status, page)
            val repeated = reports.findReviewIdsGroupedByReview(status, page)
            assertThat(first.content).containsExactlyElementsOf(expectedPage)
            assertThat(repeated.content).containsExactlyElementsOf(expectedPage)
            assertThat(first.totalElements).isEqualTo(4L)
            assertThat(first.totalPages).isEqualTo(2)
            actual.addAll(first.content)

            val response = service.getReports(status, page)
            assertThat(response.reports.map { it.reviewId }).containsExactlyElementsOf(expectedPage)
            assertThat(response.totalElements).isEqualTo(4L)
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates()

        val expectedReportIds = (groups.take(1) + groups.drop(1).asReversed()).flatMap { group ->
            when (status) {
                null -> group.reportIds.asReversed()
                ReportStatus.PENDING -> listOf(group.reportIds.first())
                ReportStatus.REJECTED -> listOf(group.reportIds.last())
                else -> error("지원하지 않는 테스트 상태: $status")
            }
        }
        assertThat(reports.findAllByReviewIdsAndStatus(expected, status).map { it.id })
            .containsExactlyElementsOf(expectedReportIds)
    }

    private fun saveGroup(index: Int, createdDate: LocalDateTime): Group {
        val writer = User("order-writer-$index", "order-writer-$index@test.com", "password")
        em.persist(writer)
        val record = UserGame(writer, game)
        em.persist(record)
        val review = Review(record, BigDecimal("4.0"), "리뷰 $index", false)
        em.persist(review)
        val reportIds = reporters.mapIndexed { reporterIndex, reporter ->
            val report = ReviewReport(review, reporter, "신고 $reporterIndex")
            if (reporterIndex == 1) report.changeStatus(ReportStatus.REJECTED)
            em.persist(report)
            ReflectionTestUtils.setField(report, "createdDate", createdDate)
            requireNotNull(report.id)
        }
        return Group(requireNotNull(review.id), reportIds)
    }

    private data class Group(val reviewId: Long, val reportIds: List<Long>)
}
