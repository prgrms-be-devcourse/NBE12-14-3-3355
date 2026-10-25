package com.gamelog.nbe121423355.domain.notification;

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse;
import com.gamelog.nbe121423355.domain.game.entity.Game;
import com.gamelog.nbe121423355.domain.review.entity.*;
import com.gamelog.nbe121423355.domain.review.service.*;
import com.gamelog.nbe121423355.domain.review.dto.request.*;
import com.gamelog.nbe121423355.domain.user.entity.User;
import com.gamelog.nbe121423355.domain.user.service.UserFollowService;
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame;
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class NotificationIntegrationTest {
    @Autowired EntityManager em;
    @Autowired NotificationService notifications;
    @Autowired NotificationRepository repository;
    @Autowired NotificationRetentionService retention;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ReviewLikeService reviewLikes;
    @Autowired ReviewCommentService comments;
    @Autowired ReviewReportService reports;
    @Autowired UserFollowService follows;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    User author, reader, third;
    Review review;

    @BeforeEach void setup() {
        author = new User("알림작성자", "notice-author@test.com", "password");
        reader = new User("알림독자", "notice-reader@test.com", "password");
        third = new User("세번째", "notice-third@test.com", "password");
        em.persist(author); em.persist(reader); em.persist(third);
        var game = Game.createFromIgdb(new IgdbGameResponse(76543L, "알림 게임", "", null, 1704067200L,
                BigDecimal.TEN, List.of(), List.of(), List.of(), List.of()));
        em.persist(game);
        var ug = new UserGame(author, game); em.persist(ug);
        review = new Review(ug, BigDecimal.ONE, "리뷰", false); em.persist(review); em.flush();
    }
    String token(User user) { return "Bearer " + jwt.generateAccessToken(user.getId(), "USER"); }
    ReviewCommentService.Body body(String text, Long parent) { return new ReviewCommentService.Body(text, false, parent); }

    @Test void likesExcludeSelfCancelAndRecreateUnread() {
        reviewLikes.addLike(author.getId(), review.getId());
        assertThat(notifications.unread(author.getId())).isZero();
        reviewLikes.addLike(reader.getId(), review.getId());
        var first = notifications.list(author.getId(), null).getItems().getFirst();
        notifications.read(author.getId(), first.getId());
        reviewLikes.removeLike(reader.getId(), review.getId());
        assertThat(notifications.list(author.getId(), null).getItems()).isEmpty();
        reviewLikes.addLike(reader.getId(), review.getId());
        assertThat(notifications.unread(author.getId())).isEqualTo(1);
        assertThat(notifications.list(author.getId(), null).getItems().getFirst().getId()).isNotEqualTo(first.getId());
    }
    @Test void followIsIdempotentAndCancelRemovesNotice() {
        follows.follow(reader.getId(), author.getId()); follows.follow(reader.getId(), author.getId());
        assertThat(notifications.unread(author.getId())).isEqualTo(1);
        follows.unfollow(reader.getId(), author.getId());
        assertThat(notifications.unread(author.getId())).isZero();
        follows.follow(reader.getId(), author.getId());
        assertThat(notifications.unread(author.getId())).isEqualTo(1);
    }
    @Test void repliesReachBothAuthorsOnceAndDeletionCancelsCommentAndLikeNotices() {
        var root = comments.create(review.getId(), reader.getId(), body("원댓글", null));
        notifications.readAll(author.getId());
        var reply = comments.create(review.getId(), third.getId(), body("답글", root.id()));
        assertThat(notifications.unread(author.getId())).isEqualTo(1);
        assertThat(notifications.unread(reader.getId())).isEqualTo(1);
        assertThat(notifications.unread(third.getId())).isZero();
        comments.like(review.getId(), reply.id(), reader.getId(), true);
        comments.like(review.getId(), reply.id(), reader.getId(), true);
        assertThat(notifications.unread(third.getId())).isEqualTo(1);
        comments.like(review.getId(), reply.id(), reader.getId(), false);
        assertThat(notifications.unread(third.getId())).isZero();
        comments.like(review.getId(), reply.id(), reader.getId(), true);
        comments.delete(review.getId(), reply.id(), third.getId());
        assertThat(notifications.unread(reader.getId())).isZero();
        assertThat(notifications.unread(author.getId())).isZero();
        assertThat(notifications.unread(third.getId())).isZero();
        var ownRoot = comments.create(review.getId(), author.getId(), body("작성자 댓글", null));
        comments.create(review.getId(), third.getId(), body("작성자에게 답글", ownRoot.id()));
        assertThat(notifications.unread(author.getId())).isEqualTo(1);
    }
    @Test void hiddenNoticeContainsNoReportTextAndIsCreatedOnlyOnActualTransition() {
        var first = reports.createReport(reader.getId(), review.getId(), new ReviewReportCreateRequest("신고자 비공개 내용"));
        reports.createReport(third.getId(), review.getId(), new ReviewReportCreateRequest("다른 비공개 내용"));
        assertThat(notifications.unread(author.getId())).isZero();
        reports.updateStatus(first.reportId(), new ReviewReportStatusUpdateRequest(ReportStatus.APPROVED));
        var item = notifications.list(author.getId(), null).getItems().getFirst();
        assertThat(item.getKind()).isEqualTo(NotificationKind.REVIEW_HIDDEN);
        assertThat(item.getGameTitle()).isEqualTo("알림 게임");
        assertThat(item.getActorName()).isNull();
        assertThat(item.getUrl()).isNull();
        assertThat(item.getUnavailable()).isFalse();
        assertThat(notifications.unread(author.getId())).isEqualTo(1);
        assertThatThrownBy(() -> reports.updateStatus(first.reportId(), new ReviewReportStatusUpdateRequest(ReportStatus.APPROVED))).hasMessageContaining("이미 처리");
    }
    @Test void apiScopesOwnershipAndOpeningListDoesNotRead() throws Exception {
        follows.follow(reader.getId(), author.getId());
        var id = notifications.list(author.getId(), null).getItems().getFirst().getId();
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications").header("Authorization", token(reader)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(put("/api/v1/notifications/" + id + "/read").header("Authorization", token(reader)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications").header("Authorization", token(author)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].read").value(false));
        assertThat(notifications.unread(author.getId())).isEqualTo(1);
        mvc.perform(put("/api/v1/notifications/read-all").header("Authorization", token(reader))).andExpect(status().isOk());
        assertThat(notifications.unread(author.getId())).isEqualTo(1);
        mvc.perform(put("/api/v1/notifications/" + id + "/read").header("Authorization", token(author)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.read").value(true));
        mvc.perform(get("/api/v1/notifications?before=0").header("Authorization", token(author))).andExpect(status().isBadRequest());
    }
    @Test void deletedTargetsAndWithdrawnActorsAreSafe() {
        reviewLikes.addLike(reader.getId(), review.getId());
        reader.withdraw("random"); review.deleteByUser(); em.flush();
        var item = notifications.list(author.getId(), null).getItems().getFirst();
        assertThat(item.getActorName()).isEqualTo("탈퇴한 사용자");
        assertThat(item.getUnavailable()).isTrue();
        assertThat(item.getUrl()).isNull();
    }
    @Test void cursorPaginationAndReadAllKeepTheirBoundaries() {
        for (int i = 0; i < 23; i++) events.publishEvent(new NotificationRequested(Set.of(author.getId()), reader.getId(), NotificationKind.REVIEW_LIKE,
            NotificationTarget.REVIEW, review.getId(), "page:" + i, review.getId(), "알림 게임"));
        var first = notifications.list(author.getId(), null);
        assertThat(first.getItems()).hasSize(20);
        var second = notifications.list(author.getId(), first.getNextCursor());
        assertThat(second.getItems()).hasSize(3);
        assertThat(second.getNextCursor()).isNull();
        assertThat(second.getItems()).extracting(NotificationService.Item::getId).doesNotContainAnyElementsOf(first.getItems().stream().map(NotificationService.Item::getId).toList());
        notifications.readAll(author.getId());
        assertThat(notifications.unread(author.getId())).isZero();
    }
    @Test void rollbackRemovesNotificationWithSourceAction() {
        var template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long[] recipient = new Long[1];
        template.executeWithoutResult(tx -> {
            var a = new User("롤백수신자", "rollback-recipient@test.com", "password");
            var b = new User("롤백행위자", "rollback-actor@test.com", "password");
            em.persist(a); em.persist(b); em.flush(); recipient[0] = a.getId();
            follows.follow(b.getId(), a.getId());
            assertThat(notifications.unread(a.getId())).isEqualTo(1);
            tx.setRollbackOnly();
        });
        assertThat(notifications.unread(recipient[0])).isZero();
    }
    @Test void unreadFilterUsesServerPaginationAndKeepsOwnership() throws Exception {
        for (int i = 0; i < 25; i++) {
            var n = new Notification(author.getId(), reader.getId(), NotificationKind.FOLLOW, NotificationTarget.USER,
                reader.getId(), null, null, "filter:" + i);
            if (i % 5 == 0) n.read();
            repository.save(n);
        }
        repository.save(new Notification(author.getId(), null, NotificationKind.REVIEW_HIDDEN, NotificationTarget.REVIEW,
            review.getId(), review.getId(), "알림 게임", "filter:hidden"));
        follows.follow(author.getId(), third.getId());
        em.flush();
        var first = notifications.list(author.getId(), null, true);
        assertThat(first.getItems()).hasSize(20).allMatch(n -> !n.getRead());
        assertThat(first.getItems().getFirst().getKind()).isEqualTo(NotificationKind.REVIEW_HIDDEN);
        assertThat(first.getNextCursor()).isNotNull();
        var next = notifications.list(author.getId(), first.getNextCursor(), true);
        assertThat(next.getItems()).hasSize(1).allMatch(n -> !n.getRead());
        assertThat(next.getNextCursor()).isNull();
        assertThat(notifications.unread(author.getId())).isEqualTo(21);
        mvc.perform(get("/api/v1/notifications?unreadOnly=true").header("Authorization", token(author)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].read").value(false));
        notifications.readAll(author.getId());
        assertThat(notifications.list(author.getId(), null, true).getItems()).isEmpty();
        assertThat(notifications.list(author.getId(), null).getItems()).hasSize(20);
        assertThat(notifications.unread(third.getId())).isEqualTo(1);
    }

    Long agedNotification(NotificationKind kind, java.time.LocalDateTime createdAt, java.time.LocalDateTime readAt, String key) {
        var n = new Notification(author.getId(), null, kind, NotificationTarget.REVIEW,
            review.getId(), review.getId(), "알림 게임", key);
        org.springframework.test.util.ReflectionTestUtils.setField(n, "createdAt", createdAt);
        org.springframework.test.util.ReflectionTestUtils.setField(n, "readAt", readAt);
        return repository.saveAndFlush(n).getId();
    }

    @Test void retentionUsesReadTimeAndIndependentHiddenDeadlineAtInclusiveBoundaries() {
        var now = java.time.LocalDateTime.of(2026, 10, 24, 12, 0);
        var expiredRead = agedNotification(NotificationKind.REVIEW_LIKE, now.minusDays(40), now.minusDays(7), "expire:read");
        var keptRead = agedNotification(NotificationKind.REVIEW_LIKE, now.minusDays(60), now.minusDays(7).plusSeconds(1), "keep:read");
        var expiredUnread = agedNotification(NotificationKind.REVIEW_COMMENT, now.minusDays(30), null, "expire:unread");
        var keptUnread = agedNotification(NotificationKind.REVIEW_COMMENT, now.minusDays(30).plusSeconds(1), null, "keep:unread");
        var expiredHiddenRead = agedNotification(NotificationKind.REVIEW_HIDDEN, now.minusDays(90), now.minusHours(1), "expire:hidden-read");
        var expiredHiddenUnread = agedNotification(NotificationKind.REVIEW_HIDDEN, now.minusDays(90), null, "expire:hidden-unread");
        var keptHiddenRead = agedNotification(NotificationKind.REVIEW_HIDDEN, now.minusDays(90).plusSeconds(1), now.minusDays(20), "keep:hidden-read");
        var keptHiddenUnread = agedNotification(NotificationKind.REVIEW_HIDDEN, now.minusDays(89), null, "keep:hidden-unread");
        em.clear();
        assertThat(retention.deleteExpiredBatch(now)).isEqualTo(4);
        assertThat(repository.findAllById(List.of(expiredRead, expiredUnread, expiredHiddenRead, expiredHiddenUnread))).isEmpty();
        assertThat(repository.findAllById(List.of(keptRead, keptUnread, keptHiddenRead, keptHiddenUnread))).hasSize(4);
        assertThat(notifications.unread(author.getId())).isEqualTo(2);
        assertThat(em.find(Review.class, review.getId())).isNotNull();
        assertThat(retention.deleteExpiredBatch(now)).isZero();
    }

    @Test void retentionDeletesInBoundedBatchesAndReadAllStartsSevenDayWindow() {
        var now = java.time.LocalDateTime.now();
        var batch = new java.util.ArrayList<Object[]>();
        for (int i = 0; i < 1005; i++) batch.add(new Object[] { author.getId(), review.getId(), review.getId(), "batch:" + i, now.minusDays(31) });
        jdbc.batchUpdate("INSERT INTO notifications (recipient_id, kind, target_type, target_id, review_id, source_key, created_at) VALUES (?, 'REVIEW_LIKE', 'REVIEW', ?, ?, ?, ?)", batch);
        var recentlyRead = agedNotification(NotificationKind.REVIEW_LIKE, now.minusDays(29), null, "read-all-kept");
        notifications.readAll(author.getId());
        em.clear();
        assertThat(retention.deleteExpiredBatch(now)).isZero();
        var readAt = repository.findById(recentlyRead).orElseThrow().getReadAt();
        assertThat(retention.deleteExpiredBatch(readAt.plusDays(7))).isEqualTo(1000);
        assertThat(retention.deleteExpiredBatch(readAt.plusDays(7))).isEqualTo(6);
        em.clear();
        assertThat(repository.findById(recentlyRead)).isEmpty();
    }

}
