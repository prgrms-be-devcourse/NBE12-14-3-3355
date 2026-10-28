package com.gamelog.nbe121423355.domain.game.sync

import com.gamelog.nbe121423355.domain.game.client.IgdbClient
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.review.entity.*
import com.gamelog.nbe121423355.domain.user.entity.*
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class IgdbSyncTest {
    @Autowired private lateinit var store: IgdbSyncStore
    @Autowired private lateinit var em: EntityManager

    companion object {
        fun game(id: Long, release: Long?): IgdbGameResponse =
            IgdbGameResponse(
                id = id,
                name = "Game $id",
                summary = "Summary",
                cover = null,
                first_release_date = release,
                rating = null,
                involved_companies = null,
                genres = null,
                platforms = null,
                collections = null,
            )
    }

    @Test
    fun fullSyncFiltersBoundariesAndAdvancesWatermarkOnlyOnCompletion() {
        val state = store.claim(1)
        val cutoff = state.cutoff
        assertThat(state.mode).isEqualTo("FULL")
        store.savePage(
            requireNotNull(state.runId),
            mutableListOf(
                game(1, null),
                game(2, -1L),
                game(3, 0L),
                game(4, cutoff),
                game(5, cutoff + 1),
            ),
        )
        assertThat(store.status().watermark).isNull()
        assertThat(store.status().inserted).isEqualTo(2)
        assertThat(store.status().skipped).isEqualTo(3)
        store.savePage(requireNotNull(state.runId), mutableListOf())
        assertThat(store.status().watermark).isEqualTo(cutoff)
        val next = store.claim(1)
        assertThat(next.mode).isEqualTo("INCREMENTAL")
        assertThat(next.sinceEpoch).isEqualTo(cutoff - 300)
        assertThat(next.cursorId).isZero()
    }

    @Test
    fun resumesFailedPageAndRejectsConcurrentOrStaleWorkers() {
        val state = store.claim(1)
        val firstRun = requireNotNull(state.runId)
        val cutoff = state.cutoff
        assertThatThrownBy { store.claim(2) }.isInstanceOf(ServiceException::class.java)
        store.savePage(firstRun, mutableListOf(game(10, 0L)))
        store.fail(firstRun)
        val resumed = store.claim(2)
        assertThat(resumed.cursorId).isEqualTo(10)
        assertThat(resumed.cutoff).isEqualTo(cutoff)
        assertThat(requireNotNull(resumed.runId)).isNotEqualTo(firstRun)
        assertThat(store.savePage(firstRun, mutableListOf(game(11, 0L)))).isFalse()
        store.savePage(requireNotNull(resumed.runId), mutableListOf(game(11, 0L)))
        assertThat(store.status().inserted).isEqualTo(2)
    }

    @Test
    fun stoppedSyncPreservesProgressAndRejectsLatePagesEvenAfterResume() {
        val state = store.claim(1)
        val runId = requireNotNull(state.runId)
        val cutoff = state.cutoff
        store.savePage(runId, mutableListOf(game(10, 0L)))

        assertThat(store.cancel().status).isEqualTo("STOPPED")
        assertThat(store.cancel().status).isEqualTo("STOPPED")
        store.fail(runId)
        assertThat(store.status().status).isEqualTo("STOPPED")
        assertThat(store.savePage(runId, mutableListOf(game(11, 0L)))).isFalse()
        assertThat(store.savePage(runId, mutableListOf())).isFalse()
        assertThat(store.status().watermark).isNull()

        val resumed = store.claim(2)
        assertThat(resumed.cursorId).isEqualTo(10)
        assertThat(resumed.processed).isEqualTo(1)
        assertThat(resumed.cutoff).isEqualTo(cutoff)
        assertThat(resumed.runId).isNotEqualTo(runId)
        assertThat(store.savePage(runId, mutableListOf(game(11, 0L)))).isFalse()
        store.savePage(requireNotNull(resumed.runId), mutableListOf())
        assertThat(store.cancel().status).isEqualTo("SUCCEEDED")
        assertThat(store.status().watermark).isEqualTo(cutoff)
    }

    @Test
    fun expiredLeaseCanBeResumedAfterServerRestart() {
        val state = store.claim(1)
        store.savePage(requireNotNull(state.runId), mutableListOf(game(10, 0L)))
        state.leaseUntil = 0
        val oldRun = requireNotNull(state.runId)
        val resumed = store.claim(2)
        assertThat(requireNotNull(resumed.runId)).isNotEqualTo(oldRun)
        assertThat(resumed.cursorId).isEqualTo(10)
    }

    @Test
    fun updatesPreserveGameIdAndUserRecordsButReplaceRelations() {
        val game = Game.createFromIgdb(game(10, 0L))
        em.persist(game)
        val user = User("tester", "sync@test.example", "password")
        em.persist(user)
        val record = UserGame(user, game)
        em.persist(record)
        val review = Review(record, null, "Keep me", false)
        em.persist(review)
        val oldGenre = Genre.createFromIgdb(20L, "Old genre")
        em.persist(oldGenre)
        em.persist(GameGenre(game, oldGenre))
        em.flush()
        em.clear()
        val state = store.claim(1)
        val replacement =
            IgdbGameResponse(
                id = 10L,
                name = "Changed",
                summary = "Summary",
                cover = null,
                first_release_date = 0L,
                rating = null,
                involved_companies = null,
                genres =
                    mutableListOf(
                        IgdbGameResponse.NamedResource(
                            id = 21L,
                            name = "New genre",
                        )
                    ),
                platforms = null,
                collections = null,
            )
        store.savePage(requireNotNull(state.runId), mutableListOf(replacement))
        em.flush()
        em.clear()
        assertThat(em.find(Game::class.java, requireNotNull(game.id)).title).isEqualTo("Changed")
        assertThat(em.find(Review::class.java, requireNotNull(review.id)).content)
            .isEqualTo("Keep me")
        val links =
            em.createQuery("select l from GameGenre l where l.game.id = :id", GameGenre::class.java)
                .setParameter("id", requireNotNull(game.id))
                .resultList
        assertThat(links).hasSize(1)
        assertThat(links.first().genre.igdbGenreId).isEqualTo(21L)
    }

    @Test
    fun ineligibleGameRemovesAllDependentUserRecords() {
        val game = Game.createFromIgdb(game(10, 0L))
        em.persist(game)
        val user = User("tester", "purge@test.example", "password")
        em.persist(user)
        val record = UserGame(user, game)
        em.persist(record)
        val review = Review(record, null, "Delete me", false)
        em.persist(review)
        val comment = ReviewComment(review, user, "Comment")
        em.persist(comment)
        val reply = ReviewComment(review, user, "Reply", false, comment)
        em.persist(reply)
        em.persist(ReviewCommentLike(reply, user))
        em.persist(ReviewCommentLike(comment, user))
        em.persist(ReviewLike(review, user))
        em.persist(ReviewReport(review, user, "Reason"))
        em.persist(UserFavoriteGame(user, game, 1))
        em.persist(UserPreferenceGame(user, game))
        em.flush()
        em.clear()
        val state = store.claim(1)
        store.savePage(requireNotNull(state.runId), mutableListOf(game(10, null)))
        em.flush()
        em.clear()
        assertThat(em.find(Game::class.java, requireNotNull(game.id))).isNull()
        assertThat(em.find(UserGame::class.java, requireNotNull(record.id))).isNull()
        assertThat(em.find(Review::class.java, requireNotNull(review.id))).isNull()
        assertThat(em.find(ReviewComment::class.java, requireNotNull(comment.id))).isNull()
        assertThat(em.find(ReviewComment::class.java, requireNotNull(reply.id))).isNull()
        assertThat(em.find(User::class.java, requireNotNull(user.id))).isNotNull()
        assertThat(store.status().deleted).isEqualTo(1)
    }

    @Test
    fun queryIncludesNewlyReleasedGamesEvenWithoutMetadataUpdates() {
        val query = IgdbClient.buildQuery(500, 1000L, 2000)
        assertThat(query)
            .contains(
                "id > 500",
                "updated_at >= 1000 & updated_at <= 2000",
                "| (first_release_date >= 1000 & first_release_date <= 2000)",
                "sort id asc",
                "limit 500",
            )
        assertThat(IgdbClient.buildQuery(0, null, 2000)).doesNotContain("first_release_date >=")
    }
}
