package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlTestConfiguration
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Import(MySqlTestConfiguration::class)
@ActiveProfiles("test")
@Transactional
class RelatedGamePrecomputeSnapshotRepositoryTest {

    @Autowired
    private lateinit var em: EntityManager
    @Autowired
    private lateinit var snapshots: RelatedGamePrecomputeSnapshotRepository
    @Autowired
    private lateinit var existingQuery: RelatedGameQueryRepository

    private var nextIgdbId = 8_100_000L

    @Test
    fun baselineMatchesOriginalQueryForEverySourceIncludingSelfExclusionAndTies() {
        // given: 장르 중복과 동점 및 후보 자격 경계를 포함한 게임을 준비한다.
        val rpg = genre("RPG")
        val action = genre("Action")
        val games = mutableListOf<Game>()
        for (index in 0 until 25) {
            games.add(game(if (index % 2 == 0) "80.25" else "90.00", rpg, action))
        }
        games.add(game("95.00", rpg))
        games.add(game("69.99", action))
        games.add(game("99.00"))
        em.flush()

        // when: 전체 게임의 공통 계산 스냅샷을 준비한다.
        val snapshot = snapshots.load(RelatedGamePrecomputeMode.INITIAL_LOAD, Long.MAX_VALUE, 20)

        // then: 모든 기준 게임의 후보와 점수가 기존 SQL과 같고 장르 조합 계산을 재사용한다.
        for (source in games) {
            assertMatchesExisting(snapshot, id(source), false)
        }
        assertThat(snapshot.calculatedGroupCount()).isEqualTo(3)
    }

    @Test
    fun ratingCountAverageAndInactiveReviewsMatchExistingCandidateEligibility() {
        // given: 리뷰 수와 평균 별점 및 숨김 상태가 다른 후보 게임을 준비한다.
        val rpg = genre("RPG")
        val source = game("90", rpg)
        val fewLowRatings = game("80.25", rpg)
        val fiveLowRatings = game("81", rpg)
        val boundaryAverage = game("82", rpg)
        val hiddenRatings = game("83", rpg)
        for (index in 0 until 5) {
            if (index < 4) {
                review(fewLowRatings, "1.0")
            }
            review(fiveLowRatings, "1.0")
            review(boundaryAverage, "3.5")
            review(hiddenRatings, "1.0").hideByAdmin()
        }
        em.flush()

        // when: 최초 적재 모드의 스냅샷을 준비한다.
        val snapshot = snapshots.load(RelatedGamePrecomputeMode.INITIAL_LOAD, Long.MAX_VALUE, 20)

        // then: 기존 SQL과 같은 리뷰 집계 및 후보 자격을 적용한다.
        assertMatchesExisting(snapshot, id(source), false)
        assertThat(requireNotNull(snapshot.baselineFor(id(source))).map { it.gameId })
            .contains(hiddenRatings.id, boundaryAverage.id, fewLowRatings.id)
            .doesNotContain(fiveLowRatings.id)
    }

    @Test
    fun refreshFiltersUnreadyCandidatesAndHonorsUpperBoundary() {
        // given: 준비 완료 후보와 미준비 후보를 서로 다른 ID로 저장한다.
        val rpg = genre("RPG")
        val source = game("90", rpg)
        val ready = game("80", rpg)
        val unready = game("99", rpg)
        val calculation = RelatedGameCalculation(ready)
        calculation.start(LocalDateTime.now())
        calculation.succeed(LocalDateTime.now())
        em.persist(calculation)
        em.flush()

        // when: 갱신 모드와 ID 상한이 있는 최초 적재 모드의 스냅샷을 준비한다.
        val snapshot = snapshots.load(RelatedGamePrecomputeMode.REFRESH, Long.MAX_VALUE, 20)

        val bounded = snapshots.load(RelatedGamePrecomputeMode.INITIAL_LOAD, id(ready), 20)

        // then: 갱신 모드의 준비 조건과 최초 적재의 ID 상한을 각각 유지한다.
        assertMatchesExisting(snapshot, id(source), true)
        assertThat(requireNotNull(snapshot.baselineFor(id(source))).map { it.gameId }).containsExactly(id(ready))
        assertThat(requireNotNull(bounded.baselineFor(id(source))).map { it.gameId }).doesNotContain(id(unready))
    }

    @Test
    fun allPositiveUserConditionsUseExistingQueryWhileInactiveAndWishlistOnlyDoNot() {
        // given: 플레이 상태와 좋아요 및 리뷰 상태가 다른 기준 게임을 준비한다.
        val rpg = genre("RPG")
        val played = game("90", rpg)
        val liked = game("90", rpg)
        val highlyRated = game("90", rpg)
        val hidden = game("90", rpg)
        val wishlistOnly = game("90", rpg)
        record(played).changePlayStatus(PlayStatus.DROPPED)
        record(liked).changeLiked(true)
        review(highlyRated, "4.0")
        review(hidden, "5.0").hideByAdmin()
        record(wishlistOnly).changeWishlist(true)
        em.flush()

        // when: 사용자 행동을 포함한 공통 스냅샷을 준비한다.
        val snapshot = snapshots.load(RelatedGamePrecomputeMode.INITIAL_LOAD, Long.MAX_VALUE, 20)

        // then: 유효한 행동 게임만 SQL 경로를 요구하고 나머지는 기본 계산을 사용한다.
        assertThat(snapshot.baselineFor(id(played))).isNull()
        assertThat(snapshot.baselineFor(id(liked))).isNull()
        assertThat(snapshot.baselineFor(id(highlyRated))).isNull()
        assertMatchesExisting(snapshot, id(hidden), false)
        assertMatchesExisting(snapshot, id(wishlistOnly), false)
        assertThat(snapshot.interactionSourceCount()).isEqualTo(3)
    }

    private fun assertMatchesExisting(snapshot: RelatedGamePrecomputeSnapshot, sourceId: Long, readyOnly: Boolean) {
        val expected = existingQuery.findCandidatesForPrecompute(sourceId, 20, readyOnly)
        val actual = requireNotNull(snapshot.baselineFor(sourceId))
        assertThat(actual.map { it.gameId })
            .containsExactlyElementsOf(expected.map { it.gameId })
        for (index in expected.indices) {
            assertThat(actual[index].relatedScore).isEqualByComparingTo(expected[index].relatedScore)
        }
    }

    private fun genre(name: String): Genre {
        val genre = Genre.createFromIgdb(nextIgdbId++, name)
        em.persist(genre)
        return genre
    }

    private fun game(rating: String, vararg genres: Genre): Game {
        val igdbId = nextIgdbId++
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = "Snapshot $igdbId", summary = null, cover = null,
            first_release_date = 1704067200L, rating = BigDecimal(rating),
            involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        em.persist(game)
        for (genre in genres) {
            em.persist(GameGenre(game, genre))
        }
        return game
    }

    private fun record(game: Game): UserGame {
        val number = nextIgdbId++
        val user = User("snapshot$number", "snapshot$number@test.com", "password")
        em.persist(user)
        val record = UserGame(user, game)
        em.persist(record)
        return record
    }

    private fun review(game: Game, rating: String): Review {
        val review = Review(record(game), BigDecimal(rating), null, false)
        em.persist(review)
        return review
    }

    private fun id(game: Game): Long = requireNotNull(game.id) { "저장된 게임의 ID가 필요합니다." }
}
