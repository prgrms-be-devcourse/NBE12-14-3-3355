package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCandidateRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameInputChangeRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameRefreshTargetRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeObserver
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTarget
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@MySqlIntegrationTest
class ChangedRelatedGamePrecomputeTest {

    @Autowired private lateinit var batch: RelatedGamePrecomputeBatchService

    @Autowired private lateinit var preparation: RelatedGameChangePreparation

    @Autowired private lateinit var query: RelatedGameChangeQueryService

    @Autowired private lateinit var expansion: RelatedGameChangeExpansion

    @Autowired private lateinit var recorder: RelatedGameChangeRecorder

    @Autowired private lateinit var changes: RelatedGameInputChangeRepository

    @Autowired private lateinit var targets: RelatedGameRefreshTargetRepository

    @Autowired private lateinit var store: RelatedGamePrecomputeStore

    @Autowired private lateinit var existingQuery: RelatedGameQueryRepository

    @Autowired private lateinit var candidates: RelatedGameCandidateRepository

    @Autowired private lateinit var em: EntityManager

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var manager: PlatformTransactionManager
    private val games = mutableListOf<Long>()
    private val genres = mutableListOf<Long>()

    @AfterEach
    fun cleanUp() {
        for (id in games) {
            jdbc.update("DELETE FROM game_genres WHERE game_id = ?", id)
            jdbc.update("DELETE FROM games WHERE id = ?", id)
            jdbc.update("DELETE FROM related_game_input_changes WHERE game_id = ?", id)
        }
        for (id in genres) jdbc.update("DELETE FROM genres WHERE id = ?", id)
    }

    @Test
    fun selectsSelfAndBothGenreRelationsIncludingPreviouslyUnstoredCandidates() {
        // given: 변경 전·후 장르를 각각 공유하는 기준 게임과 무관한 게임을 준비한다.
        val oldGenre = genre()
        val newGenre = genre()
        val source = game()
        val oldBase = game()
        val newBase = game()
        val unrelated = game()
        link(source, oldGenre)
        link(oldBase, oldGenre)
        link(newBase, newGenre)
        tx { recorder.record(source, RelatedGameChangeReason.GAME_INFORMATION) }
        jdbc.update("DELETE FROM game_genres WHERE game_id = ?", source)
        link(source, newGenre)
        tx { recorder.record(source, RelatedGameChangeReason.GAME_INFORMATION, listOf(oldGenre)) }

        // when: 저장된 후보 목록을 사용하지 않고 변경 원천을 영향 기준 게임으로 확장한다.
        preparation.expandPending {}

        // then: 이전·현재 장르 관계와 변경 게임 자신을 포함하고 무관한 게임은 제외한다.
        assertThat(targets.findPendingIds(0, Long.MAX_VALUE, 100)).containsExactly(source, oldBase, newBase)
        assertThat(targets.findByGameId(unrelated)).isNull()
        assertThat(requireNotNull(changes.findByGameId(source)).isPending).isFalse()
    }

    @Test
    fun expandingCapturedSourceDoesNotConsumeConcurrentNewVersion() {
        // given: 첫 버전 캡처 이후 새 변경이 커밋된 원천을 준비한다.
        val source = game()
        tx { changes.record(source, emptyList(), setOf(RelatedGameChangeReason.REVIEW), LocalDateTime.now()) }
        val captured = requireNotNull(changes.findByGameId(source))
        tx { changes.record(source, emptyList(), setOf(RelatedGameChangeReason.USER_GAME_RECORD), LocalDateTime.now()) }

        // when: 먼저 캡처한 원천의 영향 대상만 확장한다.
        expansion.expand(captured)

        // then: 대상은 선정하되 새 원천 버전과 변경 이유는 다음 실행에 남긴다.
        assertThat(requireNotNull(targets.findByGameId(source)).requiredVersion).isEqualTo(1)
        val remaining = requireNotNull(changes.findByGameId(source))
        assertThat(remaining.expandedVersion).isEqualTo(1)
        assertThat(remaining.changeVersion).isEqualTo(2)
        assertThat(remaining.isPending).isTrue()
    }

    @Test
    fun changedRunProcessesMultiplePagesAndLeavesNewReadyTransitionsForNextRun() {
        // given: 한 페이지를 초과하는 미계산 기준 게임을 초기 대기에 등록한다.
        repeat(110) { game() }
        expansion.initializeBaseline(games.last())

        // when: 변경 기반 부분 갱신으로 모든 페이지를 계산한다.
        val result = batch.run(RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.CHANGED),
            RelatedGamePrecomputeMode.REFRESH, RelatedGamePrecomputeObserver.NONE)

        // then: 정상 빈 결과도 성공 처리하고 이번 최초 READY 전환은 다음 원천으로 남긴다.
        assertThat(result.processed).isEqualTo(110)
        assertThat(result.succeeded).isEqualTo(110)
        assertThat(targets.countPending(Long.MAX_VALUE)).isZero()
        assertThat(changes.countPending()).isGreaterThanOrEqualTo(110)
    }

    @Test
    fun changedStoresSqlEquivalentTopTwentyOnlyForPendingSources() {
        // given: 같은 장르의 READY 게임 25개 중 두 개만 갱신 대기에 등록한다.
        val genre = genre()
        repeat(25) {
            val id = game()
            link(id, genre)
            val now = LocalDateTime.now()
            store.start(id, now)
            store.replace(id, emptyList(), now)
        }
        tx {
            for (id in games) changes.markExpanded(id, requireNotNull(changes.findByGameId(id)).changeVersion)
            for (id in games.take(2)) targets.request(id, setOf(RelatedGameChangeReason.REVIEW), LocalDateTime.now())
        }
        val first = games.first()
        val second = games[1]
        val notPending = games[2]
        val expectedFirst = existingQuery.findCandidatesForPrecompute(first, 20, true)
        val expectedSecond = existingQuery.findCandidatesForPrecompute(second, 20, true)

        // when: 전체 후보 정보를 재사용하여 변경 영향 게임만 갱신한다.
        val result = batch.run(RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.CHANGED),
            RelatedGamePrecomputeMode.REFRESH, RelatedGamePrecomputeObserver.NONE)

        // then: 두 게임의 순위·점수가 기존 SQL과 같고 나머지 게임의 저장 결과는 유지된다.
        assertThat(result.processed).isEqualTo(2)
        assertThat(result.succeeded).isEqualTo(2)
        val actualFirst = candidates.findByIdSourceGameIdOrderByRelatedRankAsc(first)
        val actualSecond = candidates.findByIdSourceGameIdOrderByRelatedRankAsc(second)
        assertThat(actualFirst).hasSize(20)
        assertThat(actualSecond).hasSize(20)
        for (index in 0 until 20) {
            assertThat(requireNotNull(actualFirst[index].id).candidateGameId).isEqualTo(expectedFirst[index].gameId)
            assertThat(actualFirst[index].relatedScore).isEqualByComparingTo(expectedFirst[index].relatedScore)
            assertThat(requireNotNull(actualSecond[index].id).candidateGameId).isEqualTo(expectedSecond[index].gameId)
            assertThat(actualSecond[index].relatedScore).isEqualByComparingTo(expectedSecond[index].relatedScore)
        }
        assertThat(candidates.findByIdSourceGameIdOrderByRelatedRankAsc(notPending)).isEmpty()
        assertThat(targets.findByGameId(notPending)).isNull()
        assertThat(targets.countPending(Long.MAX_VALUE)).isZero()
        assertThat(changes.countPending()).isZero()
    }

    @Test
    fun changeAfterPreparationSurvivesSuccessfulCalculation() {
        // given: 갱신 대기 한 건과 준비 완료 직후 원천을 변경할 통지를 준비한다.
        val source = game()
        expansion.initializeBaseline(source)
        val observer = object : RelatedGamePrecomputeObserver {
            override fun onPrepared(upperId: Long?, estimatedTotal: Long) {
                tx { recorder.record(source, RelatedGameChangeReason.REVIEW) }
            }
        }

        // when: 준비 이후 입력 변경이 있어도 고정된 대상 버전만 계산한다.
        batch.run(RelatedGamePrecomputeTarget(RelatedGamePrecomputeTargetType.CHANGED),
            RelatedGamePrecomputeMode.REFRESH, observer)

        // then: 캡처된 대기는 완료하지만 새 입력 변경은 미확장 원천으로 유지한다.
        assertThat(requireNotNull(targets.findByGameId(source)).isPending).isFalse()
        assertThat(requireNotNull(changes.findByGameId(source)).isPending).isTrue()
        assertThat(requireNotNull(changes.findByGameId(source)).reasonMask and RelatedGameChangeReason.REVIEW.mask).isNotZero()
    }

    @Test
    fun completionFailureRollsBackCandidateReplacementAndPreservesPendingVersion() {
        // given: 이전 정상 후보와 버전 1 갱신 대기를 가진 기준 게임을 준비한다.
        val source = game()
        val previous = game()
        val replacement = game()
        val now = LocalDateTime.now()
        store.start(source, now)
        store.replace(source, listOf(RelatedGameCandidateProjection(previous, BigDecimal.TEN)), now)
        tx { targets.request(source, setOf(RelatedGameChangeReason.REVIEW), now) }
        store.start(source, now)

        // when: 후보 INSERT 이후 잘못된 미래 버전 완료 표시로 트랜잭션을 실패시킨다.
        val failure = catchThrowable {
            store.replace(source, listOf(RelatedGameCandidateProjection(replacement, BigDecimal.ONE)), now, 2L)
        }

        // then: 후보 교체까지 롤백하고 이전 후보와 미완료 버전을 보존한다.
        assertThat(failure).isInstanceOf(RuntimeException::class.java)
        assertThat(candidates.findByIdSourceGameIdOrderByRelatedRankAsc(source).map { requireNotNull(it.id).candidateGameId })
            .containsExactly(previous)
        assertThat(requireNotNull(targets.findByGameId(source)).appliedVersion).isZero()
    }

    @Test
    fun previewIncludesOldAndCurrentGenresAndPendingTargetsWithoutConsumingChanges() {
        // given: 이전·현재 장르 영향 게임과 중복 대기, 별도 대기, 무관한 게임을 준비한다.
        val oldGenre = genre()
        val currentGenre = genre()
        val source = game()
        val oldBase = game()
        val currentBase = game()
        val pending = game()
        val unrelated = game()
        link(source, currentGenre)
        link(oldBase, oldGenre)
        link(currentBase, currentGenre)
        tx { changes.record(source, listOf(oldGenre), setOf(RelatedGameChangeReason.REVIEW), LocalDateTime.now()) }
        tx {
            targets.request(oldBase, setOf(RelatedGameChangeReason.REVIEW), LocalDateTime.now())
            targets.request(pending, setOf(RelatedGameChangeReason.REVIEW), LocalDateTime.now())
        }
        val before = requireNotNull(changes.findByGameId(source))

        // when: 두 페이지로 변경 영향 게임의 ID와 이름을 미리 조회한다.
        val first = query.preview(0, 2)
        val second = query.preview(1, 2)

        // then: 중복 없이 ID 순서로 이름을 반환하고 변경·대기 버전을 소비하지 않는다.
        assertThat(first.totalElements).isEqualTo(4)
        assertThat(first.totalPages).isEqualTo(2)
        assertThat(first.content.map { it.gameId }).containsExactly(source.toString(), oldBase.toString())
        assertThat(second.content.map { it.gameId }).containsExactly(currentBase.toString(), pending.toString())
        assertThat(first.content).allSatisfy { row -> assertThat(row.title).startsWith("Changed ") }
        assertThat(requireNotNull(changes.findByGameId(source)).changeVersion).isEqualTo(before.changeVersion)
        assertThat(requireNotNull(changes.findByGameId(source)).expandedVersion).isEqualTo(before.expandedVersion)
        assertThat(targets.countPending(Long.MAX_VALUE)).isEqualTo(2)
        assertThat(targets.findByGameId(source)).isNull()
        assertThat(targets.findByGameId(unrelated)).isNull()
    }

    @Test
    fun previewRetainsDeletedSourceGenreImpactAndValidatesPagination() {
        // given: 삭제된 원천의 이전 장르를 공유하는 게임과 완료된 대기를 준비한다.
        val oldGenre = genre()
        val source = game()
        val affected = game()
        val completed = game()
        link(affected, oldGenre)
        tx { changes.record(source, listOf(oldGenre), setOf(RelatedGameChangeReason.GAME_DELETED), LocalDateTime.now()) }
        jdbc.update("DELETE FROM games WHERE id = ?", source)
        tx {
            targets.request(completed, setOf(RelatedGameChangeReason.REVIEW), LocalDateTime.now())
            targets.markApplied(completed, 1)
        }

        // when: 삭제된 게임의 영향과 잘못된 페이지 범위를 조회한다.
        val result = query.preview(0, 20)
        val negativePage = catchThrowable { query.preview(-1, 20) }
        val excessiveSize = catchThrowable { query.preview(0, 101) }

        // then: 삭제된 자신과 완료된 대기는 제외하고 남은 영향 게임만 반환한다.
        assertThat(result.content.map { it.gameId }).containsExactly(affected.toString())
        assertThat(negativePage).isInstanceOf(ServiceException::class.java)
        assertThat(excessiveSize).isInstanceOf(ServiceException::class.java)
    }

    private fun <T> tx(action: () -> T): T? = TransactionTemplate(manager).execute { action() }

    private fun game(): Long = requireNotNull(tx {
        val igdbId = ids.incrementAndGet()
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = "Changed $igdbId", summary = null, cover = null,
            first_release_date = 1704067200L, rating = BigDecimal.valueOf(80),
            involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        em.persist(game)
        em.flush()
        requireNotNull(game.id).also { games.add(it) }
    })

    private fun genre(): Long = requireNotNull(tx {
        val genre = Genre.createFromIgdb(ids.incrementAndGet(), "Changed genre ${ids.get()}")
        em.persist(genre)
        em.flush()
        requireNotNull(genre.id).also { genres.add(it) }
    })

    private fun link(gameId: Long, genreId: Long) {
        tx { em.persist(GameGenre(em.getReference(Game::class.java, gameId), em.getReference(Genre::class.java, genreId))) }
    }

    companion object {
        private val ids = AtomicLong(4_900_000)
    }
}
