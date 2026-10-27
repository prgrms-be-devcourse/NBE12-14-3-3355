package com.gamelog.nbe121423355.domain.review.repository;

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse;
import com.gamelog.nbe121423355.domain.game.entity.Game;
import com.gamelog.nbe121423355.domain.game.repository.GameRepository;
import com.gamelog.nbe121423355.domain.review.entity.Review;
import com.gamelog.nbe121423355.domain.review.entity.ReviewLike;
import com.gamelog.nbe121423355.domain.review.entity.ReviewLikeId;
import jakarta.persistence.EntityManager;
import com.gamelog.nbe121423355.domain.user.entity.User;
import com.gamelog.nbe121423355.domain.user.repository.UserRepository;
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame;
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class ReviewLikeRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ReviewLikeRepository reviewLikeRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserGameRepository userGameRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameRepository gameRepository;

    @Test
    @DisplayName("새로 만든 동일 복합키로 좋아요를 조회하고 삭제한다")
    void findAndDeleteLikeUsingEquivalentKey() {
        Game game = saveGame(3301L, "복합키 검증 게임");
        User author = saveUser(41, "작성자");
        User liker = saveUser(42, "좋아요 사용자");
        Review review = saveReview(author, game, "복합키 검증 리뷰", BigDecimal.ONE, false);
        reviewLikeRepository.saveAndFlush(new ReviewLike(review, liker));
        Long userId = liker.getId();
        Long reviewId = review.getId();
        entityManager.clear();

        var found = reviewLikeRepository.findById(new ReviewLikeId(userId, reviewId));

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getUser().getId()).isEqualTo(userId);
        assertThat(found.orElseThrow().getReview().getId()).isEqualTo(reviewId);
        reviewLikeRepository.deleteById(new ReviewLikeId(userId, reviewId));
        reviewLikeRepository.flush();
        entityManager.clear();
        assertThat(reviewLikeRepository.existsById(new ReviewLikeId(userId, reviewId))).isFalse();
    }

    private Game saveGame(Long igdbId, String title) {
        IgdbGameResponse response = new IgdbGameResponse(
                igdbId,
                title,
                "테스트 게임 설명",
                new IgdbGameResponse.Cover(
                        igdbId,
                        "//images.igdb.com/" + igdbId + ".jpg"
                ),
                1704067200L,
                new BigDecimal("90.50"),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        return gameRepository.save(Game.createFromIgdb(response));
    }

    private User saveUser(int number, String nickname) {
        User user = new User(
                nickname,
                "popular-review-" + number + "@test.com",
                "password"
        );
        user.updateProfile(
                nickname,
                "https://image.test/users/" + number + ".jpg",
                null
        );
        return userRepository.save(user);
    }

    private Review saveReview(
            User author,
            Game game,
            String content,
            BigDecimal rating,
            boolean spoiler
    ) {
        UserGame userGame = userGameRepository.save(
                new UserGame(author, game)
        );
        return reviewRepository.saveAndFlush(
                new Review(userGame, rating, content, spoiler)
        );
    }

}
