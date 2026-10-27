package com.gamelog.nbe121423355.domain.board.service

import com.gamelog.nbe121423355.domain.board.dto.BoardPostRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardReportRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardReportStatusRequest
import com.gamelog.nbe121423355.domain.board.entity.BoardPostStatus
import com.gamelog.nbe121423355.domain.board.repository.BoardPostRepository
import com.gamelog.nbe121423355.domain.board.repository.BoardReportRepository
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@MySqlIntegrationTest
class BoardReportConcurrencyTest
@Autowired
constructor(
    private val postService: BoardPostService,
    private val reportService: BoardReportService,
    private val users: UserRepository,
    private val posts: BoardPostRepository,
    private val reports: BoardReportRepository,
) {
    @Test
    @DisplayName("같은 사용자가 같은 글을 동시에 여러 번 신고하면 하나만 접수되고 나머지는 409-40이다")
    fun report_concurrentDuplicateRequests() {
        // given
        val postId = createPost()
        val reporterId = createUser("dup-reporter")

        // when
        val results = runConcurrently(10) { reportService.reportPost(reporterId, postId, BoardReportRequest("중복 신고")) }

        // then: DB 제약 충돌(409-3)이 아니라 서비스의 중복 검사(409-40)로 거절된다
        assertThat(results.filter { it == SUCCESS }).hasSize(1)
        assertThat(results.filter { it != SUCCESS }).hasSize(9).allMatch { it == "409-40" }
        assertThat(reports.count()).isEqualTo(1)
    }

    @Test
    @DisplayName("신고 승인과 새 신고가 동시에 들어와도 숨겨진 글에 대기 신고가 남지 않는다")
    fun approve_concurrentWithNewReports() {
        // given
        val postId = createPost()
        val firstReportId = checkNotNull(reportService.reportPost(createUser("first"), postId, BoardReportRequest("첫 신고")).id)
        val reporterIds = (1..9).map { createUser("late-$it") }

        // when: 승인 1건과 새 신고 9건을 동시에 보낸다
        val tasks = listOf<() -> Unit>({ reportService.updateStatus(firstReportId, BoardReportStatusRequest(ReportStatus.APPROVED)) }) +
            reporterIds.map { id -> { reportService.reportPost(id, postId, BoardReportRequest("늦은 신고")); Unit } }
        val results = runConcurrently(tasks)

        // then: 승인 전에 접수된 신고는 함께 승인되고, 승인 뒤 신고는 숨겨진 글이라 404-40으로 거절된다
        assertThat(results.first()).isEqualTo(SUCCESS)
        assertThat(results.drop(1)).allMatch { it == SUCCESS || it == "404-40" }
        assertThat(posts.findById(postId).orElseThrow().status).isEqualTo(BoardPostStatus.HIDDEN_BY_ADMIN)
        assertThat(reports.findAll().map { it.status }).doesNotContain(ReportStatus.PENDING)
    }

    @AfterEach
    fun tearDown() {
        reports.deleteAll()
        posts.deleteAll()
        users.deleteAll()
    }

    private fun createUser(name: String): Long = checkNotNull(users.save(User(name, "$name@board-report.test", "password")).id)

    private fun createPost(): Long =
        checkNotNull(postService.create(createUser("writer"), BoardPostRequest("신고 대상", "본문")).id)

    private fun runConcurrently(count: Int, task: () -> Unit): List<String> = runConcurrently(List(count) { task })

    // 각 작업 결과를 성공 또는 resultCode(예외 이름)로 돌려준다. 순서는 tasks 순서와 같다
    private fun runConcurrently(tasks: List<() -> Unit>): List<String> {
        val executor = Executors.newFixedThreadPool(tasks.size)
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)
        val results = Array(tasks.size) { "" }
        val futures = tasks.mapIndexed { index, task ->
            executor.submit(Runnable {
                ready.countDown()
                start.await()
                results[index] = try {
                    task()
                    SUCCESS
                } catch (e: ServiceException) {
                    e.resultCode
                } catch (e: Exception) {
                    e.javaClass.simpleName
                }
            })
        }
        ready.await()
        start.countDown()
        futures.forEach { it.get() }
        executor.shutdown()
        return results.toList()
    }

    companion object {
        private const val SUCCESS = "OK"
    }
}
