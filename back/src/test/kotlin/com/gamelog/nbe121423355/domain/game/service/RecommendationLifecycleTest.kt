package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculationStatus
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidate
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCandidateBatchRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCandidateRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncCatalog
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.util.AopTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(properties = [
    "recommendation.publication.ready-only=true",
    "recommendation.related.precomputed-enabled=true",
    "recommendation.personalized.precomputed-enabled=true",
])
@MySqlIntegrationTest
class RecommendationLifecycleTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @Autowired private lateinit var imports: GameImportService
    @Autowired private lateinit var games: GameRepository
    @Autowired private lateinit var calculations: RelatedGameCalculationRepository
    @Autowired private lateinit var candidates: RelatedGameCandidateRepository
    @Autowired private lateinit var batch: RelatedGamePrecomputeBatchService
    @Autowired private lateinit var lock: RelatedGamePrecomputeLock
    @Autowired private lateinit var details: GameDetailService
    @Autowired private lateinit var catalog: GameService
    @Autowired private lateinit var syncCatalog: IgdbSyncCatalog
    @Autowired private lateinit var personalized: PersonalizedGameRecommendationService
    @MockitoSpyBean private lateinit var query: RelatedGameQueryRepository
    @MockitoSpyBean private lateinit var writer: RelatedGameCandidateBatchRepository
    private lateinit var tx: TransactionTemplate
    private val previousSuccess = LocalDateTime.of(2026, 10, 20, 12, 0)

    @BeforeEach
    fun setUp() {
        tx = TransactionTemplate(transactions)
    }

    @AfterEach
    fun cleanUp() {
        tx.executeWithoutResult {
            em.createQuery("delete from UserGame").executeUpdate()
            em.createQuery("delete from RelatedGameCandidate").executeUpdate()
            em.createQuery("delete from RelatedGameCalculation").executeUpdate()
            em.createQuery("delete from GameGenre").executeUpdate()
            em.createQuery("delete from Game").executeUpdate()
            em.createQuery("delete from Genre").executeUpdate()
            em.createQuery("delete from User").executeUpdate()
        }
    }

    @Test
    fun failedNewGameRemainsSearchableWhileManualRetryPreparesRecommendations() {
        // given: 기존 후보를 저장하고 신규 게임의 첫 계산이 실패하도록 준비한다.
        val candidateId = requireNotNull(tx.execute { id(readyGame(100L, "기존 후보")) })
        doThrow(IllegalStateException("계산 오류"))
            .`when`(query).findCandidatesForPrecompute(anyLong(), eq(20), eq(true))
        imports.saveGames(mutableListOf(response(200L, "신규 게임")))
        val sourceId = id(checkNotNull(games.findByIgdbId(200L)))
        val failed = calculations.findById(sourceId).orElseThrow()
        assertThat(failed.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.FAILED)
        assertThat(failed.lastSuccessAt).isNull()
        assertSearchable(sourceId, "신규 게임")
        doCallRealMethod().`when`(query).findCandidatesForPrecompute(anyLong(), eq(20), eq(true))

        // when: 실패한 신규 게임을 수동으로 재계산한다.
        val retry = batch.run(false, listOf(sourceId), RelatedGamePrecomputeMode.REFRESH)

        // then: 재시도 성공 후 저장된 추천을 사용하고 검색 노출도 유지한다.
        assertThat(retry.succeeded).isEqualTo(1)
        assertThat(retry.failedIds).isEmpty()
        assertThat(calculations.findById(sourceId).orElseThrow().isReady).isTrue()
        assertThat(details.getRelatedGames(sourceId).map { it.id }).containsExactly(candidateId)
        assertThat(catalog.getSuggestions("신규 게임").map { it.id }).containsExactly(sourceId)
    }

    @Test
    fun failureAfterJdbcInsertRollsBackReplacementAndKeepsGamePublicWhileBatchContinues() {
        // given: 이전 정상 후보를 저장하고 JDBC INSERT 직후 오류가 발생하도록 준비한다.
        val ids = requireNotNull(tx.execute {
            val source = readyGame(100L, "기존 기준")
            val previous = readyGame(200L, "이전 후보")
            val next = readyGame(300L, "다음 기준")
            em.persist(RelatedGameCandidate(source, previous, BigDecimal("77.1234"), 1))
            listOf(id(source), id(previous), id(next))
        })
        val writerTarget = AopTestUtils.getUltimateTargetObject<RelatedGameCandidateBatchRepository>(writer)
        doAnswer { invocation ->
            invocation.callRealMethod()
            throw IllegalStateException("INSERT 후 오류")
        }.`when`(writerTarget).insertAll(eq(ids.first()), anyList())

        // when: 실패할 기준 게임과 다음 기준 게임을 함께 갱신한다.
        val result = batch.run(false, listOf(ids.first(), ids.last()), RelatedGamePrecomputeMode.REFRESH)

        // then: 실패 게임의 과거 점수·공개 상태를 보존하고 다음 게임 계산은 성공한다.
        assertThat(result.failedIds).containsExactly(ids.first())
        assertThat(result.succeeded).isEqualTo(1)
        val state = calculations.findById(ids.first()).orElseThrow()
        assertThat(state.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.FAILED)
        assertThat(state.lastSuccessAt).isEqualTo(previousSuccess)
        val related = details.getRelatedGames(ids.first())
        assertThat(related.map { it.id }).containsExactly(ids[1])
        assertThat(related.first().recommendationScore).isEqualByComparingTo("77.1234")
        assertThat(details.getGameDetail(ids.first()).id).isEqualTo(ids.first())
        assertThat(calculations.findById(ids.last()).orElseThrow().lastAttemptStatus)
            .isEqualTo(RelatedGameCalculationStatus.SUCCESS)
    }

    @Test
    fun realLockContentionPreservesNewPendingGameUntilRetry() {
        // given: 공개 가능한 기존 후보를 저장하고 공통 잠금을 먼저 획득한다.
        tx.executeWithoutResult { readyGame(100L, "기존 후보") }

        // when: 잠금 경합 중 신규 게임을 등록하여 즉시 계산을 시도한다.
        val sourceId = requireNotNull(lock.tryRun { checkOwnership ->
            imports.saveGames(mutableListOf(response(200L, "대기 게임")))
            val id = id(checkNotNull(games.findByIgdbId(200L)))
            val state = calculations.findById(id).orElseThrow()
            assertThat(state.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.PENDING)
            assertThat(state.lastSuccessAt).isNull()
            assertSearchable(id, "대기 게임")
            checkOwnership()
            id
        })

        // then: 대기 상태를 보존하며 잠금 해제 후 재시도하면 추천 준비를 완료한다.
        assertThat(batch.run(false, listOf(sourceId), RelatedGamePrecomputeMode.REFRESH).exitCode()).isZero()
        assertThat(details.getRelatedGames(sourceId)).hasSize(1)
        assertThat(calculations.findById(sourceId).orElseThrow().isReady).isTrue()
    }

    @Test
    fun syncDeletionCascadesBothCandidateDirectionsAndCalculationWithoutDeletingSurvivors() {
        // given: 삭제 게임의 양방향 추천 관계와 살아남을 게임 사이의 관계를 저장한다.
        val ids = requireNotNull(tx.execute {
            val removed = readyGame(100L, "삭제 대상")
            val first = readyGame(200L, "남을 기준")
            val second = readyGame(300L, "남을 후보")
            em.persist(RelatedGameCandidate(removed, first, BigDecimal.TEN, 1))
            em.persist(RelatedGameCandidate(first, removed, BigDecimal.TEN, 1))
            em.persist(RelatedGameCandidate(first, second, BigDecimal.ONE, 2))
            listOf(id(removed), id(first), id(second))
        })

        // when: 수집 자격을 잃은 게임을 실제 동기화 경로로 삭제한다.
        tx.executeWithoutResult {
            val ineligible = IgdbGameResponse(
                id = 100L, name = "삭제 대상", summary = null, cover = null, first_release_date = null,
                rating = null, involved_companies = mutableListOf(), genres = mutableListOf(),
                platforms = mutableListOf(), collections = mutableListOf(),
            )
            assertThat(syncCatalog.apply(mutableListOf(ineligible), Instant.now().epochSecond).deleted).isEqualTo(1)
        }

        // then: 삭제 게임의 관계·상태만 제거하고 남은 후보 관계와 준비 상태는 유지한다.
        assertThat(games.existsById(ids.first())).isFalse()
        assertThat(calculations.existsById(ids.first())).isFalse()
        assertThat(candidates.count()).isEqualTo(1)
        assertThat(details.getRelatedGames(ids[1]).map { it.id }).containsExactly(ids.last())
        assertThat(calculations.findById(ids[1]).orElseThrow().isReady).isTrue()
    }

    @Test
    fun personalizedFallbackExcludesRecordedAndUnreadyGamesAndKeepsRankScoring() {
        // given: 기록·미준비 후보와 개인별 제외 후 다섯 개 미만인 저장 후보를 준비한다.
        val fixture = requireNotNull(tx.execute {
            val source = readyGame(100L, "기록 기준")
            val pool = (1..6).map { readyGame(200L + it, "후보 $it") }
            game(999L, "미준비 후보")
            em.persist(RelatedGameCandidate(source, pool[0], BigDecimal("999"), 1))
            em.persist(RelatedGameCandidate(source, pool[1], BigDecimal("888"), 2))
            val user = User("boundary", "boundary@test.com", "password")
            em.persist(user)
            record(user, source, PlayStatus.COMPLETED)
            record(user, pool.first(), PlayStatus.DROPPED)
            PersonalizedFixture(requireNotNull(user.id), pool.drop(1).map { id(it) })
        })

        // when: 저장 후보 부족으로 실시간 보충이 필요한 맞춤 추천을 조회한다.
        val result = personalized.recommend(fixture.userId)

        // then: 기록·미준비 게임을 제외하고 순위 점수 70점·67점을 유지한다.
        assertThat(result.map { it.id }).containsExactlyElementsOf(fixture.expectedIds)
        assertThat(result[0].recommendationScore).isEqualByComparingTo("70")
        assertThat(result[1].recommendationScore).isEqualByComparingTo("67")
    }

    private fun assertSearchable(id: Long, title: String) {
        assertThat(details.getGameDetail(id).id).isEqualTo(id)
        assertThat(details.getRelatedGames(id)).hasSize(1)
        assertThat(catalog.getSuggestions(title).map { it.id }).containsExactly(id)
    }

    private fun readyGame(igdbId: Long, title: String): Game {
        val game = game(igdbId, title)
        val state = RelatedGameCalculation(game)
        state.start(previousSuccess)
        state.succeed(previousSuccess)
        em.persist(state)
        return game
    }

    private fun game(igdbId: Long, title: String): Game {
        val game = Game.createFromIgdb(response(igdbId, title))
        em.persist(game)
        val genre = em.createQuery("select genre from Genre genre where genre.igdbGenreId = 10", Genre::class.java)
            .resultList.firstOrNull() ?: Genre.createFromIgdb(10L, "RPG").also { em.persist(it) }
        em.persist(GameGenre(game, genre))
        return game
    }

    private fun response(igdbId: Long, title: String) = IgdbGameResponse(
        id = igdbId, name = title, summary = null, cover = null, first_release_date = 0L,
        rating = BigDecimal("80"), involved_companies = mutableListOf(),
        genres = mutableListOf(IgdbGameResponse.NamedResource(10L, "RPG")),
        platforms = mutableListOf(), collections = mutableListOf(),
    )

    private fun record(user: User, game: Game, status: PlayStatus) {
        val record = UserGame(user, game)
        record.changePlayStatus(status)
        em.persist(record)
    }

    private fun id(game: Game): Long = requireNotNull(game.id) { "저장된 게임의 ID가 필요합니다." }

    private data class PersonalizedFixture(val userId: Long, val expectedIds: List<Long>)
}
