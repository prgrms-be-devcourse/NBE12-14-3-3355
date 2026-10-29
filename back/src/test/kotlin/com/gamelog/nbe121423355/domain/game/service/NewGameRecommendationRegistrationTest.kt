package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculationStatus
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.sync.IgdbSyncStore
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(properties = ["recommendation.publication.ready-only=true"])
@MySqlIntegrationTest
class NewGameRecommendationRegistrationTest {
    @Autowired private lateinit var imports: GameImportService
    @Autowired private lateinit var sync: IgdbSyncStore
    @Autowired private lateinit var games: GameRepository
    @Autowired private lateinit var calculations: RelatedGameCalculationRepository
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @Autowired private lateinit var em: EntityManager
    @MockitoBean private lateinit var batch: RelatedGamePrecomputeBatchService

    private lateinit var tx: TransactionTemplate

    @BeforeEach
    fun setUp() {
        tx = TransactionTemplate(transactions)
    }

    @AfterEach
    fun cleanUp() {
        tx.executeWithoutResult {
            em.createQuery("delete from RelatedGameCalculation").executeUpdate()
            em.createQuery("delete from GameGenre").executeUpdate()
            em.createQuery("delete from Game").executeUpdate()
            em.createQuery("delete from Genre").executeUpdate()
            em.createQuery("delete from IgdbSyncState").executeUpdate()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun commitsGameRelationsAndPendingStateBeforeAttemptingCalculation(useSync: Boolean) {
        // given: 실제 등록 경로에서 커밋 후 계산이 잠금 경합으로 거절되도록 준비한다.
        `when`(batch.run(eq(false), anyList(), eq(RelatedGamePrecomputeMode.REFRESH) ?: RelatedGamePrecomputeMode.REFRESH))
            .thenReturn(RelatedGamePrecomputeBatchService.Result(false, 0, 0, emptyList(), Duration.ZERO))

        // when: import 또는 동기화로 신규 게임을 저장하고 트랜잭션을 커밋한다.
        val id = requireNotNull(tx.execute {
            add(useSync)
            val game = checkNotNull(games.findByIgdbId(9700L))
            val state = calculations.findById(requireNotNull(game.id)).orElseThrow()
            assertThat(state.lastAttemptStatus).isEqualTo(RelatedGameCalculationStatus.PENDING)
            verifyNoInteractions(batch)
            requireNotNull(game.id)
        })

        // then: 커밋 이후에만 계산을 시도하고 거절돼도 대기 상태·장르를 보존하며 기존 게임 재등록은 계산하지 않는다.
        verify(batch).run(false, listOf(id), RelatedGamePrecomputeMode.REFRESH)
        // 잠금 경합으로 계산을 시작하지 못해도 커밋된 대기 상태와 장르는 유지된다.
        tx.executeWithoutResult {
            assertThat(calculations.findById(id).orElseThrow().isReady).isFalse()
            assertThat(em.createQuery("select count(link) from GameGenre link where link.game.id = :id", Long::class.javaObjectType)
                .setParameter("id", id).singleResult).isEqualTo(1)
        }
        clearInvocations(batch)
        tx.executeWithoutResult { add(useSync) }
        verifyNoInteractions(batch) // 기존 게임 업데이트는 신규 상태 생성·즉시 갱신 대상이 아니다.
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun rollbackDoesNotLeavePendingStateOrRunCalculation(useSync: Boolean) {
        // given: 신규 게임을 저장할 실제 import 또는 동기화 경로를 준비한다.
        val register = { add(useSync) }

        // when: 신규 게임 등록 트랜잭션을 롤백한다.
        tx.executeWithoutResult { status ->
            register()
            status.setRollbackOnly()
        }

        // then: 게임과 대기 상태를 남기지 않고 커밋 후 계산도 호출하지 않는다.
        assertThat(games.findByIgdbId(9700L)).isNull()
        assertThat(calculations.count()).isZero()
        verifyNoInteractions(batch)
    }

    private fun add(useSync: Boolean) {
        val response = IgdbGameResponse(
            id = 9700L, name = "신규 게임", summary = null, cover = null,
            first_release_date = 0L, rating = BigDecimal("80"),
            involved_companies = mutableListOf(),
            genres = mutableListOf(IgdbGameResponse.NamedResource(9700L, "RPG")),
            platforms = mutableListOf(), collections = mutableListOf(),
        )
        if (useSync) {
            val state = sync.claim(1L)
            sync.savePage(requireNotNull(state.runId), mutableListOf(response))
            sync.savePage(requireNotNull(state.runId), mutableListOf())
        } else {
            imports.saveGames(mutableListOf(response))
        }
    }
}
