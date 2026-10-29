package com.gamelog.nbe121423355.domain.review.controller

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewLike
import com.gamelog.nbe121423355.domain.review.repository.CommunityReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFollow
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class CommunityReviewControllerTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var repository: CommunityReviewRepository
    private var sequence = 50000L

    @Test
    fun bothFeedsRequireLogin() {
        for (tab in listOf("following", "popular")) {
            mvc.perform(get("/api/v1/community/reviews/$tab"))
                .andExpect(status().isUnauthorized)
        }
        mvc.perform(get("/api/v1/reviews/popular")).andExpect(status().isOk)
    }

    @Test
    fun followingUsesCurrentRelationsAndOnlyVisibleWrittenReviews() {
        val viewer = user()
        val author = user()
        val stranger = user()
        val old = review(author, "팔로우하기 전 리뷰")
        ReflectionTestUtils.setField(old, "createdDate", LocalDateTime.now().minusMonths(1))
        val latest = review(author, "최신 리뷰")
        val hidden = review(author, "숨긴 리뷰")
        hidden.hideByAdmin()
        val deleted = review(author, "삭제 리뷰")
        deleted.deleteByUser()
        review(author, "   ")
        review(author, null)
        review(stranger, "팔로우하지 않은 사람")
        review(viewer, "내 리뷰")
        val follow = UserFollow(viewer, author)
        em.persist(follow)
        em.persist(ReviewLike(latest, viewer))
        old.edit(BigDecimal.ONE, "수정한 오래된 리뷰", true)
        em.flush()

        mvc.perform(get("/api/v1/community/reviews/following").header("Authorization", token(viewer)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.hasFollowing").value(true))
            .andExpect(jsonPath("$.data.reviews.length()").value(2))
            .andExpect(jsonPath("$.data.reviews[0].reviewId").value(latest.id))
            .andExpect(jsonPath("$.data.reviews[0].liked").value(true))
            .andExpect(jsonPath("$.data.reviews[0].likeCount").value(1))
            .andExpect(jsonPath("$.data.reviews[1].reviewId").value(old.id))
            .andExpect(jsonPath("$.data.reviews[1].spoiler").value(true))
            .andExpect(jsonPath("$.data.reviews[1].likeCount").value(0))
            .andExpect(jsonPath("$.data.reviews[1].liked").value(false))

        em.remove(follow)
        em.flush()
        mvc.perform(get("/api/v1/community/reviews/following").header("Authorization", token(viewer)))
            .andExpect(jsonPath("$.data.hasFollowing").value(false))
            .andExpect(jsonPath("$.data.reviews").isEmpty)
    }

    @Test
    fun followingPagesTwentyReviewsAndDistinguishesEmptyFollowing() {
        val viewer = user()
        val author = user()
        em.persist(UserFollow(viewer, author))
        mvc.perform(get("/api/v1/community/reviews/following").header("Authorization", token(viewer)))
            .andExpect(jsonPath("$.data.hasFollowing").value(true))
            .andExpect(jsonPath("$.data.reviews").isEmpty)
        repeat(21) { review(author, "리뷰 $it") }
        em.flush()
        mvc.perform(get("/api/v1/community/reviews/following").header("Authorization", token(viewer)))
            .andExpect(jsonPath("$.data.reviews.length()").value(20))
            .andExpect(jsonPath("$.data.hasNext").value(true))
        mvc.perform(get("/api/v1/community/reviews/following?page=1").header("Authorization", token(viewer)))
            .andExpect(jsonPath("$.data.reviews.length()").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
        mvc.perform(get("/api/v1/community/reviews/following?page=-1").header("Authorization", token(viewer)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun popularHonorsInclusive168HourBoundaryVisibilityAndLifetimeLikes() {
        val viewer = user()
        val other = user()
        val author = user()
        val now = LocalDateTime.of(2026, 10, 20, 12, 0)
        val boundary = review(author, "정확히 168시간")
        val expired = review(author, "168시간 초과")
        val popular = review(author, "좋아요 두 개")
        val tie = review(author, "동률 최신")
        val future = review(author, "미래")
        val blank = review(author, " ")
        val hidden = review(author, "숨김")
        hidden.hideByAdmin()
        val deleted = review(author, "삭제")
        deleted.deleteByUser()
        val noLikes = review(author, "좋아요 없음")
        for (r in listOf(boundary, expired, popular, tie, future, blank, hidden, deleted, noLikes)) {
            ReflectionTestUtils.setField(r, "createdDate", now.minusHours(1))
            if (r !== noLikes) em.persist(ReviewLike(r, viewer))
        }
        ReflectionTestUtils.setField(boundary, "createdDate", now.minusHours(168))
        ReflectionTestUtils.setField(boundary, "status", null)
        ReflectionTestUtils.setField(expired, "createdDate", now.minusHours(168).minusSeconds(1))
        ReflectionTestUtils.setField(future, "createdDate", now.plusSeconds(1))
        em.persist(ReviewLike(popular, other))
        em.flush()

        val result = repository.findPopular(checkNotNull(viewer.id), now.minusHours(168), now, PageRequest.of(0, 20))
        assertThat(result.map { it.reviewId }).containsExactly(popular.id, tie.id, boundary.id)
        assertThat(result.map { it.likeCount }).containsExactly(2L, 1L, 1L)
        assertThat(repository.findPopular(checkNotNull(other.id), now.minusHours(168), now, PageRequest.of(0, 20))
            .map { it.liked }).containsExactly(true, false, false)
    }

    @Test
    fun popularCapsAtTwentyAndDoesNotFillFromOldReviews() {
        val viewer = user()
        val author = user()
        val old = review(author, "옛 리뷰")
        ReflectionTestUtils.setField(old, "createdDate", LocalDateTime.now().minusDays(8))
        em.persist(ReviewLike(old, viewer))
        em.flush()
        mvc.perform(get("/api/v1/community/reviews/popular").header("Authorization", token(viewer)))
            .andExpect(jsonPath("$.data.reviews").isEmpty)
        repeat(22) { em.persist(ReviewLike(review(author, "새 리뷰 $it"), viewer)) }
        em.flush()
        mvc.perform(get("/api/v1/community/reviews/popular").header("Authorization", token(viewer)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.reviews.length()").value(20))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun bothFeedsExposeCurrentAuthorPlayState() {
        val viewer = user()
        val author = user()
        val review = review(author, "상태가 있는 리뷰")
        em.persist(UserFollow(viewer, author))
        em.persist(ReviewLike(review, viewer))
        review.userGame.changePlayStatus(PlayStatus.COMPLETED)
        review.userGame.changePlaying(true)
        review.userGame.changeBacklog(true)
        review.userGame.changeWishlist(true)
        em.flush()
        for (tab in listOf("following", "popular")) {
            mvc.perform(get("/api/v1/community/reviews/$tab").header("Authorization", token(viewer)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.reviews[0].playStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.reviews[0].playing").value(true))
                .andExpect(jsonPath("$.data.reviews[0].backlog").value(true))
                .andExpect(jsonPath("$.data.reviews[0].wishlist").value(true))
        }
        review.userGame.clearPlayStatus()
        review.userGame.changePlaying(false)
        review.userGame.changeBacklog(false)
        review.userGame.changeWishlist(false)
        em.flush()
        for (tab in listOf("following", "popular")) {
            mvc.perform(get("/api/v1/community/reviews/$tab").header("Authorization", token(viewer)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.reviews[0].playStatus").isEmpty)
                .andExpect(jsonPath("$.data.reviews[0].playing").value(false))
                .andExpect(jsonPath("$.data.reviews[0].backlog").value(false))
                .andExpect(jsonPath("$.data.reviews[0].wishlist").value(false))
        }
    }

    private fun user(): User {
        val user = User("사용자$sequence", "community${sequence++}@test.com", "password")
        em.persist(user)
        return user
    }

    private fun review(author: User, content: String?): Review {
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = sequence++,
            name = "게임",
            summary = "설명",
            cover = null,
            first_release_date = 1704067200L,
            rating = BigDecimal.TEN,
            involved_companies = mutableListOf(),
            genres = mutableListOf(),
            platforms = mutableListOf(),
            collections = mutableListOf(),
        ))
        em.persist(game)
        val userGame = UserGame(author, game)
        em.persist(userGame)
        val review = Review(userGame, BigDecimal.ONE, content, false)
        em.persist(review)
        return review
    }

    private fun token(user: User): String = "Bearer ${jwt.generateAccessToken(user.id, "USER")}"
}
