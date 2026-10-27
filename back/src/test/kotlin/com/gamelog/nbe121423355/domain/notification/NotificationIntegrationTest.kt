package com.gamelog.nbe121423355.domain.notification

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.notification.service.NotificationRetentionService
import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.notification.entity.Notification
import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import com.gamelog.nbe121423355.domain.notification.entity.NotificationRequested
import com.gamelog.nbe121423355.domain.notification.entity.NotificationTarget
import com.gamelog.nbe121423355.domain.notification.repository.NotificationRepository
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportCreateRequest
import com.gamelog.nbe121423355.domain.review.dto.request.ReviewReportStatusUpdateRequest
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewComment
import com.gamelog.nbe121423355.domain.review.service.ReviewCommentService
import com.gamelog.nbe121423355.domain.review.service.ReviewLikeService
import com.gamelog.nbe121423355.domain.review.service.ReviewReportService
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.service.UserFollowService
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class NotificationIntegrationTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var notifications: NotificationService
    @Autowired private lateinit var repository: NotificationRepository
    @Autowired private lateinit var retention: NotificationRetentionService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var reviewLikes: ReviewLikeService
    @Autowired private lateinit var comments: ReviewCommentService
    @Autowired private lateinit var reports: ReviewReportService
    @Autowired private lateinit var follows: UserFollowService
    @Autowired private lateinit var events: ApplicationEventPublisher
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jwt: JwtProvider

    private lateinit var author: User
    private lateinit var reader: User
    private lateinit var third: User
    private lateinit var review: Review
    private val authorId get() = checkNotNull(author.id)
    private val readerId get() = checkNotNull(reader.id)
    private val thirdId get() = checkNotNull(third.id)
    private val reviewId get() = checkNotNull(review.id)

    @BeforeEach
    fun setup() {
        author = User("알림작성자", "notice-author@test.com", "password")
        reader = User("알림독자", "notice-reader@test.com", "password")
        third = User("세번째", "notice-third@test.com", "password")
        listOf(author, reader, third).forEach(em::persist)
        val game = Game.createFromIgdb(
            IgdbGameResponse(
                id = 76543L,
                name = "알림 게임",
                summary = "",
                cover = null,
                first_release_date = 1704067200L,
                rating = BigDecimal.TEN,
                involved_companies = mutableListOf(),
                genres = mutableListOf(),
                platforms = mutableListOf(),
                collections = mutableListOf(),
            )
        )
        em.persist(game)
        val record = UserGame(author, game)
        em.persist(record)
        review = Review(record, BigDecimal.ONE, "리뷰", false)
        em.persist(review)
        em.flush()
    }

    @Test
    @DisplayName("본인 좋아요는 알림을 만들지 않고 취소 후 다시 좋아요하면 새 미읽음 알림을 만든다")
    fun likesExcludeSelfCancelAndRecreateUnread() {
        reviewLikes.addLike(authorId, reviewId)

        assertThat(notifications.unread(authorId)).isZero()

        reviewLikes.addLike(readerId, reviewId)
        val first = notifications.list(authorId, null).items.first()
        notifications.read(authorId, first.id)
        reviewLikes.removeLike(readerId, reviewId)

        assertThat(notifications.list(authorId, null).items).isEmpty()

        reviewLikes.addLike(readerId, reviewId)

        assertThat(notifications.unread(authorId)).isEqualTo(1L)
        assertThat(notifications.list(authorId, null).items.first().id).isNotEqualTo(first.id)
    }

    @Test
    @DisplayName("중복 팔로우는 알림을 중복 생성하지 않고 언팔로우 후 재팔로우하면 다시 알린다")
    fun followIsIdempotentAndCancelRemovesNotice() {
        follows.follow(readerId, authorId)
        follows.follow(readerId, authorId)

        assertThat(notifications.unread(authorId)).isEqualTo(1L)

        follows.unfollow(readerId, authorId)

        assertThat(notifications.unread(authorId)).isZero()

        follows.follow(readerId, authorId)

        assertThat(notifications.unread(authorId)).isEqualTo(1L)
    }

    @Test
    @DisplayName("답글은 리뷰·원댓글 작성자에게 중복 없이 알리고 삭제 시 관련 댓글·좋아요 알림을 취소한다")
    fun repliesReachBothAuthorsOnceAndDeletionCancelsCommentAndLikeNotices() {
        val root = comments.create(reviewId, readerId, body("원댓글"))
        notifications.readAll(authorId)
        val reply = comments.create(reviewId, thirdId, body("답글", root.id))
        val replyId = checkNotNull(reply.id)

        assertThat(notifications.unread(authorId)).isEqualTo(1L)
        assertThat(notifications.unread(readerId)).isEqualTo(1L)
        assertThat(notifications.unread(thirdId)).isZero()

        comments.like(reviewId, replyId, readerId, true)
        comments.like(reviewId, replyId, readerId, true)

        assertThat(notifications.unread(thirdId)).isEqualTo(1L)

        comments.like(reviewId, replyId, readerId, false)

        assertThat(notifications.unread(thirdId)).isZero()

        comments.like(reviewId, replyId, readerId, true)
        comments.delete(reviewId, replyId, thirdId)

        assertThat(notifications.unread(readerId)).isZero()
        assertThat(notifications.unread(authorId)).isZero()
        assertThat(notifications.unread(thirdId)).isZero()

        val ownRoot = comments.create(reviewId, authorId, body("작성자 댓글"))
        comments.create(reviewId, thirdId, body("작성자에게 답글", ownRoot.id))

        assertThat(notifications.unread(authorId)).isEqualTo(1L)
    }

    @Test
    @DisplayName("신고 승인으로 리뷰가 숨겨질 때만 비공개 정보를 제외한 알림을 생성한다")
    fun hiddenNoticeContainsNoReportTextAndIsCreatedOnlyOnActualTransition() {
        val first = reports.createReport(readerId, reviewId, ReviewReportCreateRequest("신고자 비공개 내용"))
        reports.createReport(thirdId, reviewId, ReviewReportCreateRequest("다른 비공개 내용"))

        assertThat(notifications.unread(authorId)).isZero()

        val reportId = checkNotNull(first.reportId)
        reports.updateStatus(reportId, ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))
        val item = notifications.list(authorId, null).items.first()

        assertThat(item.kind).isEqualTo(NotificationKind.REVIEW_HIDDEN)
        assertThat(item.gameTitle).isEqualTo("알림 게임")
        assertThat(item.actorName).isNull()
        assertThat(item.url).isNull()
        assertThat(item.unavailable).isFalse()
        assertThat(notifications.unread(authorId)).isEqualTo(1L)

        assertThatThrownBy {
            reports.updateStatus(reportId, ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))
        }.hasMessageContaining("이미 처리")
    }

    @Test
    @DisplayName("알림 API는 인증과 소유권을 확인하고 목록 조회만으로 읽음 처리하지 않는다")
    fun apiScopesOwnershipAndOpeningListDoesNotRead() {
        follows.follow(readerId, authorId)
        val id = notifications.list(authorId, null).items.first().id

        mvc.perform(get("/api/v1/notifications"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/api/v1/notifications").header("Authorization", token(reader)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items").isEmpty)
        mvc.perform(put("/api/v1/notifications/$id/read").header("Authorization", token(reader)))
            .andExpect(status().isNotFound)
        mvc.perform(get("/api/v1/notifications").header("Authorization", token(author)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].read").value(false))
        assertThat(notifications.unread(authorId)).isEqualTo(1L)
        mvc.perform(put("/api/v1/notifications/read-all").header("Authorization", token(reader)))
            .andExpect(status().isOk)
        assertThat(notifications.unread(authorId)).isEqualTo(1L)
        mvc.perform(put("/api/v1/notifications/$id/read").header("Authorization", token(author)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.read").value(true))
        mvc.perform(get("/api/v1/notifications?before=0").header("Authorization", token(author)))
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("작성자가 탈퇴하고 대상 리뷰가 삭제되면 대체 이름을 표시하고 링크를 제공하지 않는다")
    fun deletedTargetsAndWithdrawnActorsAreSafe() {
        reviewLikes.addLike(readerId, reviewId)
        reader.withdraw("random")
        review.deleteByUser()
        em.flush()
        val item = notifications.list(authorId, null).items.first()

        assertThat(item.actorName).isEqualTo("탈퇴한 사용자")
        assertThat(item.unavailable).isTrue()
        assertThat(item.url).isNull()
    }

    @Test
    @DisplayName("알림 23개를 커서로 20개·3개씩 중복 없이 조회하고 전체 읽음 처리한다")
    fun cursorPaginationAndReadAllKeepTheirBoundaries() {
        repeat(23) { index ->
            events.publishEvent(
                NotificationRequested(
                    recipients = setOf(authorId),
                    actorId = readerId,
                    kind = NotificationKind.REVIEW_LIKE,
                    targetType = NotificationTarget.REVIEW,
                    targetId = reviewId,
                    sourceKey = "page:$index",
                    reviewId = reviewId,
                    gameTitle = "알림 게임",
                )
            )
        }

        val first = notifications.list(authorId, null)

        assertThat(first.items).hasSize(20)

        val second = notifications.list(authorId, first.nextCursor)

        assertThat(second.items).hasSize(3)
        assertThat(second.nextCursor).isNull()
        assertThat(second.items.map { it.id })
            .doesNotContainAnyElementsOf(first.items.map { it.id })

        notifications.readAll(authorId)

        assertThat(notifications.unread(authorId)).isZero()
    }

    @Test
    @DisplayName("원본 동작의 트랜잭션이 롤백되면 함께 생성된 알림도 롤백된다")
    fun rollbackRemovesNotificationWithSourceAction() {
        val template = TransactionTemplate(transactions).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

        val recipientId = checkNotNull(template.execute { transaction ->
            val recipient = User("롤백수신자", "rollback-recipient@test.com", "password")
            val actor = User("롤백행위자", "rollback-actor@test.com", "password")
            listOf(recipient, actor).forEach(em::persist)
            em.flush()
            val id = checkNotNull(recipient.id)
            follows.follow(checkNotNull(actor.id), id)
            assertThat(notifications.unread(id)).isEqualTo(1L)
            transaction.setRollbackOnly()
            id
        })

        assertThat(notifications.unread(recipientId)).isZero()
    }

    @Test
    @DisplayName("미읽음 필터는 페이지네이션에 반영되고 전체 읽음 처리는 다른 사용자에게 영향을 주지 않는다")
    fun unreadFilterUsesServerPaginationAndKeepsOwnership() {
        repeat(25) { index ->
            repository.save(
                notice(kind = NotificationKind.FOLLOW, key = "filter:$index").apply {
                    if (index % 5 == 0) read()
                }
            )
        }

        repository.save(notice(kind = NotificationKind.REVIEW_HIDDEN, key = "filter:hidden"))
        follows.follow(authorId, thirdId)
        em.flush()
        val first = notifications.list(authorId, null, unreadOnly = true)

        assertThat(first.items).hasSize(20).allMatch { !it.read }
        assertThat(first.items.first().kind).isEqualTo(NotificationKind.REVIEW_HIDDEN)
        assertThat(first.nextCursor).isNotNull()

        val next = notifications.list(authorId, first.nextCursor, unreadOnly = true)

        assertThat(next.items).hasSize(1).allMatch { !it.read }
        assertThat(next.nextCursor).isNull()
        assertThat(notifications.unread(authorId)).isEqualTo(21L)

        mvc.perform(get("/api/v1/notifications?unreadOnly=true").header("Authorization", token(author)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[0].read").value(false))
        notifications.readAll(authorId)

        assertThat(notifications.list(authorId, null, unreadOnly = true).items).isEmpty()
        assertThat(notifications.list(authorId, null).items).hasSize(20)
        assertThat(notifications.unread(thirdId)).isEqualTo(1L)
    }

    @Test
    @DisplayName("일반 알림은 읽은 지 7일·미읽음 30일, 숨김 알림은 생성 후 90일 경계부터 삭제한다")
    fun retentionUsesReadTimeAndIndependentHiddenDeadlineAtInclusiveBoundaries() {
        val now = LocalDateTime.of(2026, 10, 24, 12, 0)
        val expiredRead = agedNotification(NotificationKind.REVIEW_LIKE, now.minusDays(40), now.minusDays(7), "expire:read")
        val keptRead = agedNotification(NotificationKind.REVIEW_LIKE, now.minusDays(60), now.minusDays(7).plusSeconds(1), "keep:read")
        val expiredUnread = agedNotification(NotificationKind.REVIEW_COMMENT, now.minusDays(30), null, "expire:unread")
        val keptUnread = agedNotification(NotificationKind.REVIEW_COMMENT, now.minusDays(30).plusSeconds(1), null, "keep:unread")
        val expiredHiddenRead = agedNotification(NotificationKind.REVIEW_HIDDEN, now.minusDays(90), now.minusHours(1), "expire:hidden-read")
        val expiredHiddenUnread = agedNotification(NotificationKind.REVIEW_HIDDEN, now.minusDays(90), null, "expire:hidden-unread")
        val keptHiddenRead = agedNotification(NotificationKind.REVIEW_HIDDEN, now.minusDays(90).plusSeconds(1), now.minusDays(20), "keep:hidden-read")
        val keptHiddenUnread = agedNotification(NotificationKind.REVIEW_HIDDEN, now.minusDays(89), null, "keep:hidden-unread")
        em.clear()

        assertThat(retention.deleteExpiredBatch(now)).isEqualTo(4)
        assertThat(repository.findAllById(listOf(expiredRead, expiredUnread, expiredHiddenRead, expiredHiddenUnread))).isEmpty()
        assertThat(repository.findAllById(listOf(keptRead, keptUnread, keptHiddenRead, keptHiddenUnread))).hasSize(4)
        assertThat(notifications.unread(authorId)).isEqualTo(2L)
        assertThat(em.find(Review::class.java, reviewId)).isNotNull()
        assertThat(retention.deleteExpiredBatch(now)).isZero()
    }

    @Test
    @DisplayName("전체 읽음 시점부터 7일이 지나면 만료 알림을 최대 1000개씩 나누어 삭제한다")
    fun retentionDeletesInBoundedBatchesAndReadAllStartsSevenDayWindow() {
        val now = LocalDateTime.now()
        val batch = List(1005) { index ->
            arrayOf<Any>(authorId, reviewId, reviewId, "batch:$index", now.minusDays(31))
        }
        jdbc.batchUpdate(
            """
                INSERT INTO notifications (recipient_id, kind, target_type, target_id, review_id, source_key, created_at)
                VALUES (?, 'REVIEW_LIKE', 'REVIEW', ?, ?, ?, ?)
            """.trimIndent(),
            batch,
        )

        val recentlyRead = agedNotification(NotificationKind.REVIEW_LIKE, now.minusDays(29), null, "read-all-kept")
        notifications.readAll(authorId)
        em.clear()

        assertThat(retention.deleteExpiredBatch(now)).isZero()

        val readAt = checkNotNull(repository.findById(recentlyRead).orElseThrow().readAt)
        assertThat(retention.deleteExpiredBatch(readAt.plusDays(7))).isEqualTo(1000)
        assertThat(retention.deleteExpiredBatch(readAt.plusDays(7))).isEqualTo(6)

        em.clear()

        assertThat(repository.findById(recentlyRead)).isEmpty()
    }

    @Test
    @DisplayName("알림 행위자와 대상 사용자가 없으면 대체 이름을 표시하고 프로필 링크를 제공하지 않는다")
    fun missingActorAndProfileTargetRemainUnavailable() {
        repository.save(
            Notification(
                recipientId = authorId,
                actorId = -1L,
                kind = NotificationKind.FOLLOW,
                targetType = NotificationTarget.USER,
                targetId = -1L,
                reviewId = null,
                gameTitle = null,
                sourceKey = "missing-user",
            )
        )
        em.flush()
        em.clear()

        val item = notifications.list(authorId, null).items.single()

        assertThat(item.actorName).isEqualTo("탈퇴한 사용자")
        assertThat(item.url).isNull()
        assertThat(item.unavailable).isTrue()
    }

    @Test
    @DisplayName("댓글이 삭제되어 알림만 남으면 기존 알림을 유지하고 링크를 제공하지 않는다")
    fun commentLinkBecomesUnavailableWhenCommentIsDeletedButNoticeRemains() {
        val comment = comments.create(reviewId, readerId, body("알림 링크 댓글"))
        val commentId = checkNotNull(comment.id)
        val item = notifications.list(authorId, null).items.single()

        assertThat(item.actorName).isEqualTo(reader.nickname)
        assertThat(item.url).isEqualTo(
            "/games/${review.userGame.game.id}?commentReview=$reviewId&notificationComment=$commentId"
        )
        assertThat(item.unavailable).isFalse()

        // 알림 취소 이벤트 없이 대상만 삭제되어도 남아 있는 알림은 안전하게 표시한다.
        em.find(ReviewComment::class.java, commentId).delete()
        em.flush()
        em.clear()

        val unavailableItem = notifications.list(authorId, null).items.single()

        assertThat(unavailableItem.id).isEqualTo(item.id)
        assertThat(unavailableItem.url).isNull()
        assertThat(unavailableItem.unavailable).isTrue()
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽어도 최초 읽음 시각을 유지한다")
    fun readingAgainPreservesOriginalReadTime() {
        val originalReadAt = LocalDateTime.of(2026, 10, 20, 12, 0)
        val id = agedNotification(
            NotificationKind.FOLLOW,
            originalReadAt.minusDays(1),
            originalReadAt,
            "already-read",
        )
        em.clear()

        assertThat(notifications.read(authorId, id).read).isTrue()
        em.flush()
        em.clear()

        assertThat(repository.findById(id).orElseThrow().readAt).isEqualTo(originalReadAt)
    }

    private fun token(user: User) = "Bearer ${jwt.generateAccessToken(checkNotNull(user.id), "USER")}"

    private fun body(text: String, parent: Long? = null) =
        ReviewCommentService.Body(content = text, spoiler = false, parentId = parent)

    private fun notice(kind: NotificationKind, key: String): Notification {
        val follow = kind == NotificationKind.FOLLOW
        return Notification(
            recipientId = authorId,
            actorId = if (follow) readerId else null,
            kind = kind,
            targetType = if (follow) NotificationTarget.USER else NotificationTarget.REVIEW,
            targetId = if (follow) readerId else reviewId,
            reviewId = if (follow) null else reviewId,
            gameTitle = if (follow) null else "알림 게임",
            sourceKey = key,
        )
    }

    private fun agedNotification(
        kind: NotificationKind,
        createdAt: LocalDateTime,
        readAt: LocalDateTime?,
        key: String,
    ): Long {
        val notification = notice(kind, key)
        ReflectionTestUtils.setField(notification, "createdAt", createdAt)
        ReflectionTestUtils.setField(notification, "readAt", readAt)
        return checkNotNull(repository.saveAndFlush(notification).id)
    }
}
