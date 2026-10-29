package com.gamelog.nbe121423355.domain.review.repository

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewLike
import com.gamelog.nbe121423355.domain.review.entity.ReviewLikeId
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class ReviewLikeRepositoryTest {
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var reviewLikeRepository: ReviewLikeRepository
    @Autowired private lateinit var reviewRepository: ReviewRepository
    @Autowired private lateinit var userGameRepository: UserGameRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var gameRepository: GameRepository

    @Test
    @DisplayName("새로 만든 동일 복합키로 좋아요를 조회하고 삭제한다")
    fun findAndDeleteLikeUsingEquivalentKey() {
        val game = saveGame(3301L, "복합키 검증 게임")
        val author = saveUser(41, "작성자")
        val liker = saveUser(42, "좋아요 사용자")
        val review = saveReview(author, game, "복합키 검증 리뷰", BigDecimal.ONE, false)
        reviewLikeRepository.saveAndFlush(ReviewLike(review, liker))
        val userId = checkNotNull(liker.id)
        val reviewId = checkNotNull(review.id)
        entityManager.clear()

        val found = reviewLikeRepository.findById(ReviewLikeId(userId, reviewId))

        assertThat(found).isPresent()
        assertThat(found.orElseThrow().user.id).isEqualTo(userId)
        assertThat(found.orElseThrow().review.id).isEqualTo(reviewId)
        reviewLikeRepository.deleteById(ReviewLikeId(userId, reviewId))
        reviewLikeRepository.flush()
        entityManager.clear()
        assertThat(reviewLikeRepository.existsById(ReviewLikeId(userId, reviewId))).isFalse()
    }

    private fun saveGame(igdbId: Long, title: String): Game {
        val response = IgdbGameResponse(
            id = igdbId,
            name = title,
            summary = "테스트 게임 설명",
            cover = IgdbGameResponse.Cover(igdbId, "//images.igdb.com/$igdbId.jpg"),
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

    private fun saveReview(
        author: User,
        game: Game,
        content: String,
        rating: BigDecimal,
        spoiler: Boolean,
    ): Review {
        val userGame = userGameRepository.save(UserGame(author, game))
        return reviewRepository.saveAndFlush(Review(userGame, rating, content, spoiler))
    }
}
