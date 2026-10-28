package com.gamelog.nbe121423355.domain.notification

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.notification.entity.Notification
import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import com.gamelog.nbe121423355.domain.notification.entity.NotificationTarget
import com.gamelog.nbe121423355.domain.notification.repository.NotificationRepository
import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.repository.ReviewLikeRepository
import com.gamelog.nbe121423355.domain.review.service.ReviewLikeService
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFollowId
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.service.UserFollowService
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@MySqlIntegrationTest
class NotificationTransactionTest @Autowired constructor(
    private val em: EntityManager,
    private val notifications: NotificationService,
    private val likes: ReviewLikeService,
    private val follows: UserFollowService,
    private val likeRepository: ReviewLikeRepository,
    private val followRepository: UserFollowRepository,
    transactionManager: PlatformTransactionManager,
) {
    @MockitoSpyBean private lateinit var repository: NotificationRepository
    private val transactions = TransactionTemplate(transactionManager)
    private var recipientId = 0L
    private var actorId = 0L
    private var reviewId = 0L
    private var gameId = 0L

    @BeforeEach
    fun setup() {
        transactions.executeWithoutResult {
            val recipient = User("알림수신자", "after-commit-recipient@test.com", "password")
            val actor = User("알림행위자", "after-commit-actor@test.com", "password")
            em.persist(recipient)
            em.persist(actor)
            val game = Game.createFromIgdb(IgdbGameResponse(876543L, "커밋 알림 게임", "",
                null, null, null, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()))
            em.persist(game)
            val record = UserGame(recipient, game)
            em.persist(record)
            val review = Review(record, BigDecimal.ONE, "리뷰", false)
            em.persist(review)
            em.flush()
            recipientId = checkNotNull(recipient.id)
            actorId = checkNotNull(actor.id)
            reviewId = checkNotNull(review.id)
            gameId = checkNotNull(game.id)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["like", "follow"])
    @DisplayName("좋아요·팔로우 알림은 원본 트랜잭션 커밋 후에만 저장된다")
    fun notificationIsWrittenOnlyAfterCommit(source: String) {
        transactions.executeWithoutResult {
            createSource(source)
            assertThat(notifications.unread(recipientId)).isZero()
        }
        assertThat(sourceExists(source)).isTrue()
        assertThat(notifications.unread(recipientId)).isEqualTo(1L)
    }

    @ParameterizedTest
    @ValueSource(strings = ["like", "follow"])
    @DisplayName("원본 좋아요·팔로우가 롤백되면 알림을 저장하지 않는다")
    fun rollbackDoesNotCreateNotification(source: String) {
        transactions.executeWithoutResult { status ->
            createSource(source)
            status.setRollbackOnly()
        }
        assertThat(sourceExists(source)).isFalse()
        assertThat(notifications.unread(recipientId)).isZero()
    }

    @ParameterizedTest
    @ValueSource(strings = ["like", "follow"])
    @DisplayName("알림 저장 중 실패하면 알림만 롤백하고 좋아요·팔로우는 성공한다")
    fun saveFailureDoesNotUndoSource(source: String) {
        doAnswer { invocation ->
            invocation.callRealMethod()
            em.flush()
            throw IllegalStateException("알림 저장 실패")
        }.`when`(repository).save(anyNotification())

        assertThatCode { createSource(source) }.doesNotThrowAnyException()
        assertThat(sourceExists(source)).isTrue()
        assertThat(notifications.unread(recipientId)).isZero()
    }

    @ParameterizedTest
    @ValueSource(strings = ["like", "follow"])
    @DisplayName("알림 별도 트랜잭션의 커밋이 실패해도 좋아요·팔로우는 성공한다")
    fun notificationCommitFailureDoesNotUndoSource(source: String) {
        doAnswer { invocation ->
            val saved = invocation.callRealMethod()
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun beforeCommit(readOnly: Boolean) {
                    throw IllegalStateException("알림 커밋 실패")
                }
            })
            saved
        }.`when`(repository).save(anyNotification())

        assertThatCode { createSource(source) }.doesNotThrowAnyException()
        assertThat(sourceExists(source)).isTrue()
        assertThat(notifications.unread(recipientId)).isZero()
    }

    @ParameterizedTest
    @ValueSource(strings = ["like", "follow"])
    @DisplayName("알림 취소가 실패해도 좋아요 취소·언팔로우는 성공한다")
    fun cancelFailureDoesNotUndoCancellation(source: String) {
        createSource(source)
        assertThat(notifications.unread(recipientId)).isEqualTo(1L)
        doThrow(IllegalStateException("알림 취소 실패")).`when`(repository).deleteBySourceKey(anyString())

        assertThatCode {
            if (source == "like") likes.removeLike(actorId, reviewId)
            else follows.unfollow(actorId, recipientId)
        }.doesNotThrowAnyException()
        assertThat(sourceExists(source)).isFalse()
        assertThat(notifications.unread(recipientId)).isEqualTo(1L)
    }

    private fun createSource(source: String) {
        if (source == "like") likes.addLike(actorId, reviewId)
        else follows.follow(actorId, recipientId)
    }

    private fun sourceExists(source: String): Boolean =
        if (source == "like") likeRepository.existsByReview_IdAndUser_Id(reviewId, actorId)
        else followRepository.existsById(UserFollowId(actorId, recipientId))

    private fun anyNotification(): Notification = any(Notification::class.java) ?: Notification(
        recipientId, actorId, NotificationKind.FOLLOW, NotificationTarget.USER, actorId, null, null, "matcher",
    )

    @AfterEach
    fun cleanup() {
        reset(repository)
        transactions.executeWithoutResult {
            em.createQuery("delete from Notification n where n.recipientId = :id")
                .setParameter("id", recipientId).executeUpdate()
            em.createQuery("delete from ReviewLike l where l.review.id = :id")
                .setParameter("id", reviewId).executeUpdate()
            em.createQuery("delete from Review r where r.id = :id").setParameter("id", reviewId).executeUpdate()
            em.createQuery("delete from UserGame ug where ug.game.id = :id").setParameter("id", gameId).executeUpdate()
            em.createQuery("delete from UserFollow f where f.follower.id = :id")
                .setParameter("id", actorId).executeUpdate()
            em.createQuery("delete from Game g where g.id = :id").setParameter("id", gameId).executeUpdate()
            em.createQuery("delete from User u where u.id in :ids")
                .setParameter("ids", listOf(recipientId, actorId)).executeUpdate()
        }
    }
}
