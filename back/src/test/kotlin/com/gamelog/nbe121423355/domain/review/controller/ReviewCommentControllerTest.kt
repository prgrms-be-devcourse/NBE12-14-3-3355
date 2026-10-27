package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.repository.ReviewCommentLikeRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewCommentRepository
import com.gamelog.nbe121423355.domain.review.service.ReviewCommentService
import com.gamelog.nbe121423355.domain.review.service.ReviewCommentService.Body
import com.gamelog.nbe121423355.domain.review.service.ReviewService
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class ReviewCommentControllerTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var service: ReviewCommentService
    @Autowired private lateinit var reviewService: ReviewService
    @Autowired private lateinit var comments: ReviewCommentRepository
    @Autowired private lateinit var likes: ReviewCommentLikeRepository

    private lateinit var author: User
    private lateinit var reader: User
    private lateinit var review: Review

    private val reviewId: Long get() = requireNotNull(review.id)
    private val authorId: Long get() = requireNotNull(author.id)
    private val readerId: Long get() = requireNotNull(reader.id)

    @BeforeEach
    fun setup() {
        author = User("작성자", "comment-author@test.com", "password")
        reader = User("독자", "comment-reader@test.com", "password")
        em.persist(author)
        em.persist(reader)
        val game = Game.createFromIgdb(
            IgdbGameResponse(
                id = 98765L,
                name = "게임",
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
        val userGame = UserGame(author, game)
        em.persist(userGame)
        review = Review(userGame, BigDecimal.ONE, "리뷰", false)
        em.persist(review)
        em.flush()
    }

    private fun base(): String = "/api/v1/reviews/$reviewId/comments"
    private fun token(user: User): String = "Bearer ${jwt.generateAccessToken(user.id, "USER")}"
    private fun body(text: String, parent: Long?): Body = Body(text, false, parent)

    @Test
    fun anonymousReadsButCannotWriteAndInvalidContentIsRejected() {
        mvc.perform(get(base()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(0))
        mvc.perform(post(base()).contentType(MediaType.APPLICATION_JSON).content("""{"content":"댓글"}"""))
            .andExpect(status().isUnauthorized)
        for (text in listOf("   ", "x".repeat(1001))) {
            mvc.perform(
                post(base()).header("Authorization", token(reader))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"$text","spoiler":false}""")
            ).andExpect(status().isBadRequest)
        }
        mvc.perform(
            post(base()).header("Authorization", token(reader))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content":"댓글\n두 줄","spoiler":true}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.spoiler").value(true))
            .andExpect(jsonPath("$.data.mine").value(true))
    }

    @Test
    fun repliesHaveOneLevelAndCannotCrossReviews() {
        val root = service.create(reviewId, authorId, body("원댓글", null))
        val reply = service.create(reviewId, readerId, body("답글", root.id))
        assertThat(root.author).isTrue()
        assertThatThrownBy { service.create(reviewId, readerId, body("중첩", reply.id)) }
            .hasMessageContaining("답글")
        // 다른 리뷰를 대상으로 한 접근은 조회 단계에서 거부된다.
        assertThatThrownBy { service.list(999999L, null, root.id, null) }
            .hasMessageContaining("리뷰")
        assertThat(service.list(reviewId, null, root.id, null).comments).hasSize(1)
    }

    @Test
    fun likesAreIdempotentAndOwnedEditsKeepOrder() {
        val comment = service.create(reviewId, authorId, body("첫 댓글", null))
        val commentId = requireNotNull(comment.id)
        service.like(reviewId, commentId, readerId, true)
        val liked = service.like(reviewId, commentId, readerId, true)
        assertThat(liked.likeCount).isEqualTo(1)
        assertThat(liked.liked).isTrue()
        assertThat(service.list(reviewId, authorId, null, null).comments.first().liked).isFalse()
        mvc.perform(
            put("${base()}/$commentId").header("Authorization", token(reader))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content":"변조","spoiler":false}""")
        ).andExpect(status().isForbidden)
        mvc.perform(delete("${base()}/$commentId").header("Authorization", token(reader)))
            .andExpect(status().isForbidden)
        val edited = service.edit(reviewId, commentId, authorId, Body("수정", true, null))
        assertThat(edited.edited).isTrue()
        assertThat(edited.createdDate).isEqualTo(comment.createdDate)
        assertThat(service.like(reviewId, commentId, readerId, false).likeCount).isZero()
    }

    @Test
    fun deletedParentRedactsIdentityKeepsRepliesAndBlocksNewReplies() {
        val root = service.create(reviewId, authorId, body("비밀 원댓글", null))
        val rootId = requireNotNull(root.id)
        val reply = service.create(reviewId, readerId, body("답글", rootId))
        val replyId = requireNotNull(reply.id)
        service.like(reviewId, rootId, readerId, true)
        service.delete(reviewId, rootId, authorId)
        val page = service.list(reviewId, null, null, null)
        assertThat(page.totalCount).isEqualTo(1)
        val tombstone = page.comments.first()
        assertThat(tombstone.deleted).isTrue()
        assertThat(tombstone.userId).isNull()
        assertThat(tombstone.nickname).isNull()
        assertThat(tombstone.content).isEmpty()
        assertThat(tombstone.likeCount).isZero()
        assertThatThrownBy { service.create(reviewId, readerId, body("새 답글", rootId)) }
            .hasMessageContaining("댓글")
        service.edit(reviewId, replyId, readerId, body("답글 수정", rootId))
        service.delete(reviewId, replyId, readerId)
        assertThat(service.list(reviewId, null, null, null).comments).isEmpty()
        assertThat(service.count(reviewId)).isZero()
    }

    @Test
    fun pagingIsBoundedAndDeletedRowsDoNotConsumeSlots() {
        val root = service.create(reviewId, authorId, body("원댓글", null))
        repeat(20) { service.create(reviewId, readerId, body("댓글$it", null)) }
        repeat(11) { service.create(reviewId, readerId, body("답글$it", root.id)) }
        val first = service.list(reviewId, null, null, null)
        assertThat(first.comments).hasSize(20)
        assertThat(first.totalCount).isEqualTo(32)
        assertThat(service.list(reviewId, null, null, first.nextCursor).comments).hasSize(1)
        val replies = service.list(reviewId, null, root.id, null)
        assertThat(replies.comments).hasSize(10)
        assertThat(service.list(reviewId, null, root.id, replies.nextCursor).comments).hasSize(1)
    }

    @Test
    fun reviewDeletionPurgesThreadsAndLikesBeforeRestore() {
        val root = service.create(reviewId, authorId, body("원댓글", null))
        val reply = service.create(reviewId, readerId, body("답글", root.id))
        service.like(reviewId, requireNotNull(reply.id), authorId, true)
        reviewService.deleteReview(authorId, reviewId, review.version)
        assertThat(comments.count()).isZero()
        assertThat(likes.count()).isZero()
        review.restore(BigDecimal.ONE, "다시 작성", false)
        em.flush()
        assertThat(service.count(reviewId)).isZero()
    }

    @Test
    fun hiddenReviewsKeepDataButDenyReadsAndWrites() {
        val comment = service.create(reviewId, readerId, body("댓글", null))
        review.hideByAdmin()
        em.flush()
        mvc.perform(get(base())).andExpect(status().isNotFound)
        mvc.perform(get("${base()}/count")).andExpect(status().isNotFound)
        assertThatThrownBy { service.like(reviewId, requireNotNull(comment.id), readerId, true) }
            .hasMessageContaining("리뷰")
        assertThatThrownBy { service.create(reviewId, readerId, body("댓글", null)) }
            .hasMessageContaining("리뷰")
        assertThat(comments.count()).isEqualTo(1)
    }
}
