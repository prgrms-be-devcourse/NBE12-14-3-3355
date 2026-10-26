package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse.NamedResource
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class GameImportServiceIntegrationTest {
    @Autowired private lateinit var imports: GameImportService
    @Autowired private lateinit var games: GameRepository
    @Autowired private lateinit var em: EntityManager

    @Test
    fun reimportUpdatesGamesWithoutDuplicatingSharedResourcesOrLinks() {
        val first = response(81001L, "First")
        val second = response(81002L, "Second")
        assertThat(imports.saveGames(mutableListOf(first, second))).isEqualTo(2)
        em.flush()
        em.clear()
        val firstId = requireNotNull(games.findByIgdbId(81001L).orElseThrow().id)
        val secondId = requireNotNull(games.findByIgdbId(81002L).orElseThrow().id)

        assertThat(imports.saveGames(mutableListOf(response(81001L, "Updated"), second)))
            .isEqualTo(2)
        em.flush()
        em.clear()

        val updated = games.findByIgdbId(81001L).orElseThrow()
        assertThat(requireNotNull(updated.id)).isEqualTo(firstId)
        assertThat(updated.title).isEqualTo("Updated")
        assertThat(games.findByIgdbId(81002L).orElseThrow().id).isEqualTo(secondId)
        assertThat(games.count()).isEqualTo(2L)
        assertThat(count("Genre")).isEqualTo(1L)
        assertThat(count("Platform")).isEqualTo(1L)
        assertThat(count("GameSeries")).isEqualTo(1L)
        for (id in listOf(firstId, secondId)) {
            assertLinks(id)
        }
    }

    @Test
    fun missingRelationsInReimportPreserveExistingLinks() {
        imports.saveGames(mutableListOf(response(81001L, "First")))
        em.flush()
        em.clear()
        val id = requireNotNull(games.findByIgdbId(81001L).orElseThrow().id)

        imports.saveGames(
            mutableListOf(
                IgdbGameResponse(
                    id = 81001L,
                    name = "Updated",
                    summary = "Summary",
                    cover = null,
                    first_release_date = 0L,
                    rating = null,
                    involved_companies = null,
                    genres = null,
                    platforms = null,
                    collections = null,
                )
            )
        )
        em.flush()
        em.clear()

        assertThat(games.findByIgdbId(81001L).orElseThrow().id).isEqualTo(id)
        assertLinks(id)
    }

    private fun assertLinks(gameId: Long) {
        for (entity in listOf("GameGenre", "GamePlatform", "GameSeriesGame")) {
            assertThat(
                    em.createQuery(
                            "select count(l) from $entity l where l.game.id = :id",
                            Long::class.javaObjectType,
                        )
                        .setParameter("id", gameId)
                        .singleResult
                )
                .`as`(entity)
                .isEqualTo(1L)
        }
    }

    private fun count(entity: String): Long =
        em.createQuery("select count(e) from $entity e", Long::class.javaObjectType).singleResult

    private fun response(id: Long, title: String): IgdbGameResponse =
        IgdbGameResponse(
            id = id,
            name = title,
            summary = "Summary",
            cover = null,
            first_release_date = 0L,
            rating = null,
            involved_companies = null,
            genres =
                mutableListOf(
                    NamedResource(
                        id = 82001L,
                        name = "Action",
                    )
                ),
            platforms =
                mutableListOf(
                    NamedResource(
                        id = 83001L,
                        name = "PC",
                    )
                ),
            collections =
                mutableListOf(
                    NamedResource(
                        id = 84001L,
                        name = "Shared series",
                    )
                ),
        )
}
