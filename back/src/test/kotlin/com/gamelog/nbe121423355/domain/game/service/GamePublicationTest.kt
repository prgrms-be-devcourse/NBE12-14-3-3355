package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameSearchRequest
import com.gamelog.nbe121423355.domain.game.dto.GameSort
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidate
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.GameStatisticsRepository
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRecommendationCandidateQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.PersonalizedRelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCandidateRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(properties = [
    "recommendation.publication.ready-only=true",
    "recommendation.related.precomputed-enabled=true",
    "recommendation.personalized.precomputed-enabled=true",
])
@MySqlIntegrationTest
@Transactional
class GamePublicationTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var games: GameService
    @Autowired private lateinit var details: GameDetailService
    @Autowired private lateinit var gameRepository: GameRepository
    @Autowired private lateinit var statistics: GameStatisticsRepository
    @Autowired private lateinit var related: RelatedGameQueryRepository
    @Autowired private lateinit var stored: RelatedGameCandidateRepository
    @Autowired private lateinit var personalized: PersonalizedRelatedGameQueryRepository
    @Autowired private lateinit var candidates: PersonalizedRecommendationCandidateQueryRepository
    @Autowired private lateinit var imports: GameImportService
    @Autowired private lateinit var calculations: RelatedGameCalculationRepository
    @Autowired private lateinit var transactions: PlatformTransactionManager

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun committedNewGameIsCalculatedAndPublishedUsingRealMysqlLockAndStore() {
        // given: 독립 트랜잭션으로 공개 가능한 기존 후보와 신규 등록에 사용할 장르를 준비한다.
        val tx = TransactionTemplate(transactions)
        var genreId: Long? = null
        try {
            genreId = tx.execute {
                val genre = genre()
                val candidate = game(1600L, "ready candidate", genre)
                ready(candidate, failedRefresh = false)
                requireNotNull(genre.id)
            }

            // when: 신규 게임을 등록하고 커밋 후 실제 잠금·계산·저장 흐름을 실행한다.
            val id = requireNotNull(tx.execute {
                imports.saveGames(mutableListOf(IgdbGameResponse(
                    id = 1700L, name = "new game", summary = null, cover = null,
                    first_release_date = 0L, rating = BigDecimal("80"),
                    involved_companies = mutableListOf(),
                    genres = mutableListOf(IgdbGameResponse.NamedResource(10L, "RPG")),
                    platforms = mutableListOf(), collections = mutableListOf(),
                )))
                val game = gameRepository.findByIgdbId(1700L).orElseThrow()
                val gameId = requireNotNull(game.id)
                assertThat(calculations.findById(gameId).orElseThrow().isReady).isFalse()
                gameId
            })

            // then: 커밋 후 계산을 완료하고 기존 후보를 포함한 공개 연관 추천을 반환한다.
            assertThat(calculations.findById(id).orElseThrow().isReady).isTrue()
            assertThat(details.getRelatedGames(id).map { it.title }).containsExactly("ready candidate")
        } finally {
            val createdGenreId = genreId
            tx.executeWithoutResult {
                em.createQuery("delete from GameGenre link where link.game.igdbId in (1600, 1700)").executeUpdate()
                em.createQuery("delete from Game game where game.igdbId in (1600, 1700)").executeUpdate()
                if (createdGenreId != null) em.createQuery("delete from Genre genre where genre.id = :id")
                    .setParameter("id", createdGenreId).executeUpdate()
            }
        }
    }

    @Test
    fun catalogIncludesUnreadyGamesInPaginationCountAndSuggestions() {
        // given: 미준비 게임 여덟 개와 정상 성공·갱신 실패 후 준비된 게임 두 개를 저장한다.
        val genre = genre()
        val unready = (0 until 8).map { game(100L + it, "Game pending $it", genre) }
        val first = game(200L, "Game ready 1", genre)
        val second = game(201L, "Game ready 2", genre)
        ready(first, failedRefresh = false)
        ready(second, failedRefresh = true)
        em.flush()

        // when: 기본 목록·필터 목록·자동완성을 조회한다.
        val page = games.getGamesPage(GameSearchRequest(0, 1, null, null, null, null))
        val filteredPages = (listOf(null) + GameSort.entries).map { sort ->
            games.getGamesPage(GameSearchRequest(
                page = 0, size = 1, sort = sort, keyword = "Game",
                genreIds = mutableListOf(requireNotNull(genre.id)), platformIds = null,
            ))
        }
        val suggestions = games.getSuggestions("Game")

        // then: 추천 공개 설정이 켜져 있어도 검색은 계산 상태와 무관하게 페이지·총수·후보를 반환한다.
        assertThat(page.totalElements).isEqualTo(10)
        assertThat(page.content.map { it.id }).containsExactly(unready.first().id)
        for (filtered in filteredPages) {
            assertThat(filtered.totalElements).isEqualTo(10)
            assertThat(filtered.content.map { it.id }).containsExactly(unready.first().id)
        }
        assertThat(suggestions.map { it.id }).containsExactlyElementsOf(unready.take(6).map { it.id })
    }

    @Test
    fun opensUnreadyDetailsAndFallsBackToRealtimeRecommendations() {
        // given: 미준비 게임과 성공한 빈 결과 이후 갱신에 실패한 게임을 저장한다.
        val genre = genre()
        val unready = game(300L, "unready", genre)
        val published = game(301L, "published", genre)
        ready(published, failedRefresh = true)
        em.flush()

        // when: 두 게임의 상세·연관 추천을 조회한다.
        val unreadyDetail = details.getGameDetail(id(unready))
        val fallback = details.getRelatedGames(id(unready))
        val detail = details.getGameDetail(id(published))
        val relatedGames = details.getRelatedGames(id(published))

        // then: 미준비 게임도 열 수 있고, 계산 전에는 기존 실시간 추천 경로를 사용한다.
        assertThat(unreadyDetail.id).isEqualTo(unready.id)
        assertThat(fallback.map { it.id }).containsExactly(published.id)
        assertThat(detail.id).isEqualTo(published.id)
        assertThat(relatedGames).isEmpty()
    }

    @Test
    fun filtersEveryRecommendationCandidatePathBeforeRanking() {
        // given: 미준비 후보가 더 높은 저장 순위를 가진 준비 완료 기준 게임을 저장한다.
        val genre = genre()
        val source = game(400L, "source", genre)
        val hidden = game(401L, "hidden", genre)
        val visible = game(402L, "visible", genre)
        ready(source, failedRefresh = false)
        ready(visible, failedRefresh = false)
        em.persist(RelatedGameCandidate(source, hidden, BigDecimal("999"), 1))
        em.persist(RelatedGameCandidate(source, visible, BigDecimal("55"), 2))
        em.flush()
        val sourceIds = listOf(id(source))

        // when: 상세·개인화의 저장·실시간 경로와 장르 후보·최종 정보 조회를 실행한다.
        val realtime = related.findRelatedGames(id(source))
        val saved = stored.findStoredRelatedGames(id(source), PageRequest.of(0, 5))
        val personalizedRows = listOf(
            personalized.findStoredTopFiveBySourceGameIds(sourceIds, emptyList()),
            personalized.findTopFiveBySourceGameIds(sourceIds, emptyList()),
        )
        val genreCandidates = candidates.findTopFiveIdsByPreferredGenres(listOf(requireNotNull(genre.id)), sourceIds)
        val candidateDetails = candidates.findAllByGameIds(listOf(id(hidden), id(visible)))

        // then: 모든 경로가 미준비 후보를 먼저 제외하고 개인별 순위를 1위로 다시 매긴다.
        assertThat(realtime.map { it.id }).containsExactly(visible.id)
        assertThat(saved.map { it.id }).containsExactly(visible.id)
        for (rows in personalizedRows) {
            assertThat(rows.map { it.gameId }).containsExactly(visible.id)
            assertThat(rows.first().relatedRank).isEqualTo(1)
        }
        assertThat(genreCandidates).containsExactly(visible.id)
        assertThat(candidateDetails.map { it.gameId }).containsOnly(visible.id)
    }

    @Test
    fun popularGamesExcludeUnreadyBeforeLimit() {
        // given: 좋아요가 있는 준비 완료 게임과 더 큰 ID의 미준비 게임을 저장한다.
        val genre = genre()
        val visible = game(500L, "visible", genre)
        val hidden = game(501L, "hidden", genre)
        ready(visible, failedRefresh = false)
        val user = User("publication", "publication@test.com", "password")
        em.persist(user)
        for (game in listOf(visible, hidden)) {
            val record = UserGame(user, game)
            record.changeLiked(true)
            em.persist(record)
        }
        em.flush()

        // when: 인기 게임 조회 개수를 한 개로 제한한다.
        val result = statistics.findPopularGames(PageRequest.of(0, 1))

        // then: 제한 적용 전에 미준비 게임을 제외하여 준비된 게임을 반환한다.
        assertThat(result.map { it.gameId }).containsExactly(visible.id)
    }

    private fun genre(): Genre {
        val genre = Genre.createFromIgdb(10L, "RPG")
        em.persist(genre)
        return genre
    }

    private fun game(id: Long, title: String, genre: Genre): Game {
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = id, name = title, summary = null, cover = null, first_release_date = 1704067200L,
            rating = BigDecimal("80"), involved_companies = mutableListOf(), genres = mutableListOf(),
            platforms = mutableListOf(), collections = mutableListOf(),
        ))
        em.persist(game)
        em.persist(GameGenre(game, genre))
        return game
    }

    private fun ready(game: Game, failedRefresh: Boolean) {
        val state = RelatedGameCalculation(game)
        state.start(LocalDateTime.now())
        state.succeed(LocalDateTime.now())
        if (failedRefresh) state.fail(LocalDateTime.now(), "refresh failed")
        em.persist(state)
    }

    private fun id(game: Game): Long = requireNotNull(game.id) { "저장된 게임의 ID가 필요합니다." }
}
