package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewLike
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class PopularReviewRepositoryTest {
    @Autowired
    private lateinit var reviewLikeRepository: ReviewLikeRepository

    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    @Autowired
    private lateinit var userGameRepository: UserGameRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var gameRepository: GameRepository

    @Test
    @DisplayName("좋아요 수를 기준으로 인기 리뷰와 카드 정보를 조회한다")
    fun findPopularReviewsOrdersByLikeCountAndReturnsCardData() {
        // given: 서로 다른 좋아요 수를 가진 리뷰와 좋아요가 없는 리뷰를 준비한다.
        val game = saveGame(3001L, "인기 리뷰 게임")
        val firstAuthor = saveUser(1, "첫 번째 작성자")
        val secondAuthor = saveUser(2, "두 번째 작성자")
        val thirdAuthor = saveUser(3, "세 번째 작성자")
        val firstLiker = saveUser(11, "첫 번째 좋아요 사용자")
        val secondLiker = saveUser(12, "두 번째 좋아요 사용자")
        val mostLiked = saveReview(firstAuthor, game, "가장 좋아요가 많은 리뷰", BigDecimal("4.5"), spoiler = true)
        val lessLiked = saveReview(secondAuthor, game, "좋아요가 한 개인 리뷰", BigDecimal("4.0"), spoiler = false)
        saveReview(thirdAuthor, game, "좋아요가 없는 리뷰", BigDecimal("3.5"), spoiler = false)
        saveLike(mostLiked, firstLiker)
        saveLike(mostLiked, secondLiker)
        saveLike(lessLiked, firstLiker)

        // when: 인기 리뷰를 최대 5개 조회한다.
        val result = reviewLikeRepository.findPopularReviews(PageRequest.of(0, 5))

        // then: 좋아요 수 내림차순으로 정렬되고 카드 정보와 집계값이 반환된다.
        assertThat(result.map { it.reviewId }).containsExactly(mostLiked.id, lessLiked.id)
        assertThat(result.map { it.likeCount }).containsExactly(2L, 1L)
        val first = result.first()
        assertThat(first.userId).isEqualTo(firstAuthor.id)
        assertThat(first.nickname).isEqualTo("첫 번째 작성자")
        assertThat(first.profileImageUrl).isEqualTo("https://image.test/users/1.jpg")
        assertThat(first.gameId).isEqualTo(game.id)
        assertThat(first.gameTitle).isEqualTo("인기 리뷰 게임")
        assertThat(first.gameCoverImageUrl).isEqualTo("https://images.igdb.com/3001.jpg")
        assertThat(first.rating).isEqualByComparingTo("4.5")
        assertThat(first.content).isEqualTo("가장 좋아요가 많은 리뷰")
        assertThat(first.spoiler).isTrue()
        assertThat(first.createdDate).isNotNull()
    }

    @Test
    @DisplayName("좋아요 수가 같으면 작성일과 리뷰 ID 내림차순으로 정렬한다")
    fun findPopularReviewsOrdersTiesByCreatedDateAndReviewId() {
        // given: 좋아요 수가 같고 작성일이 같거나 서로 다른 리뷰를 준비한다.
        val game = saveGame(3101L, "동률 정렬 게임")
        val firstAuthor = saveUser(21, "첫 번째 작성자")
        val secondAuthor = saveUser(22, "두 번째 작성자")
        val oldAuthor = saveUser(23, "이전 작성자")
        val liker = saveUser(24, "좋아요 사용자")
        val sameTimeFirst = saveReview(firstAuthor, game, "같은 시간 첫 번째 리뷰", BigDecimal("4.0"), spoiler = false)
        val sameTimeSecond = saveReview(secondAuthor, game, "같은 시간 두 번째 리뷰", BigDecimal("4.0"), spoiler = false)
        val olderReview = saveReview(oldAuthor, game, "이전 리뷰", BigDecimal("4.0"), spoiler = false)
        val sameTime = LocalDateTime.of(2026, 9, 22, 12, 0)
        ReflectionTestUtils.setField(sameTimeFirst, "createdDate", sameTime)
        ReflectionTestUtils.setField(sameTimeSecond, "createdDate", sameTime)
        ReflectionTestUtils.setField(olderReview, "createdDate", sameTime.minusDays(1))
        reviewRepository.flush()
        saveLike(sameTimeFirst, liker)
        saveLike(sameTimeSecond, liker)
        saveLike(olderReview, liker)

        // when: 좋아요 수가 같은 인기 리뷰를 조회한다.
        val result = reviewLikeRepository.findPopularReviews(PageRequest.of(0, 5))

        // then: 작성일 내림차순 후 리뷰 ID 내림차순으로 정렬한다.
        assertThat(result.map { it.reviewId }).containsExactly(sameTimeSecond.id, sameTimeFirst.id, olderReview.id)
    }

    @Test
    @DisplayName("내용이 없거나 삭제 또는 숨김 처리된 리뷰를 제외한다")
    fun findPopularReviewsExcludesInvisibleReviews() {
        // given: 공개 리뷰와 내용 없음·사용자 삭제·관리자 숨김 리뷰를 준비한다.
        val game = saveGame(3201L, "노출 조건 게임")
        val activeAuthor = saveUser(31, "공개 작성자")
        val blankAuthor = saveUser(32, "빈 내용 작성자")
        val deletedAuthor = saveUser(33, "삭제 작성자")
        val hiddenAuthor = saveUser(34, "숨김 작성자")
        val liker = saveUser(35, "좋아요 사용자")
        val activeReview = saveReview(activeAuthor, game, "공개 리뷰", BigDecimal("5.0"), spoiler = false)
        val blankReview = saveReview(blankAuthor, game, "   ", BigDecimal("4.5"), spoiler = false)
        val deletedReview = saveReview(deletedAuthor, game, "사용자가 삭제한 리뷰", BigDecimal("4.0"), spoiler = false)
        val hiddenReview = saveReview(hiddenAuthor, game, "관리자가 숨긴 리뷰", BigDecimal("3.5"), spoiler = false)
        deletedReview.deleteByUser()
        hiddenReview.hideByAdmin()
        reviewRepository.flush()
        saveLike(activeReview, liker)
        saveLike(blankReview, liker)
        saveLike(deletedReview, liker)
        saveLike(hiddenReview, liker)

        // when: 인기 리뷰를 조회한다.
        val result = reviewLikeRepository.findPopularReviews(PageRequest.of(0, 5))

        // then: 내용이 있고 공개 상태인 리뷰만 반환한다.
        assertThat(result.map { it.reviewId }).containsExactly(activeReview.id)
    }

    private fun saveGame(igdbId: Long, title: String): Game {
        val response = IgdbGameResponse(
            id = igdbId,
            name = title,
            summary = "테스트 게임 설명",
            cover = IgdbGameResponse.Cover(id = igdbId, url = "//images.igdb.com/$igdbId.jpg"),
            first_release_date = 1704067200L,
            rating = BigDecimal("90.50"),
            involved_companies = mutableListOf(),
            genres = mutableListOf(),
            platforms = mutableListOf(),
            collections = mutableListOf(),
        )
        return gameRepository.save(Game.createFromIgdb(response))
    }

    private fun saveUser(number: Int, nickname: String): User {
        val user = User(nickname, "popular-review-$number@test.com", "password")
        user.updateProfile(nickname, "https://image.test/users/$number.jpg", null)
        return userRepository.save(user)
    }

    private fun saveReview(author: User, game: Game, content: String, rating: BigDecimal, spoiler: Boolean): Review {
        val userGame = userGameRepository.save(UserGame(author, game))
        return reviewRepository.saveAndFlush(Review(userGame, rating, content, spoiler))
    }

    private fun saveLike(review: Review, liker: User) {
        reviewLikeRepository.save(ReviewLike(review, liker))
    }
}
