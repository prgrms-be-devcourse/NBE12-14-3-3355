package com.gamelog.nbe121423355.domain.game.service;

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse;
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse.NamedResource;
import com.gamelog.nbe121423355.domain.game.repository.GameRepository;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class GameImportServiceIntegrationTest {
    @Autowired GameImportService imports;
    @Autowired GameRepository games;
    @Autowired EntityManager em;

    @Test
    void reimportUpdatesGamesWithoutDuplicatingSharedResourcesOrLinks() {
        var first = response(81001L, "First");
        var second = response(81002L, "Second");
        assertThat(imports.saveGames(List.of(first, second))).isEqualTo(2);
        em.flush();
        em.clear();
        Long firstId = games.findByIgdbId(81001L).orElseThrow().getId();
        Long secondId = games.findByIgdbId(81002L).orElseThrow().getId();

        assertThat(imports.saveGames(List.of(response(81001L, "Updated"), second))).isEqualTo(2);
        em.flush();
        em.clear();

        var updated = games.findByIgdbId(81001L).orElseThrow();
        assertThat(updated.getId()).isEqualTo(firstId);
        assertThat(updated.getTitle()).isEqualTo("Updated");
        assertThat(games.findByIgdbId(81002L).orElseThrow().getId()).isEqualTo(secondId);
        assertThat(games.count()).isEqualTo(2L);
        assertThat(count("Genre")).isEqualTo(1L);
        assertThat(count("Platform")).isEqualTo(1L);
        assertThat(count("GameSeries")).isEqualTo(1L);
        for (Long id : List.of(firstId, secondId)) {
            assertLinks(id);
        }
    }

    @Test
    void missingRelationsInReimportPreserveExistingLinks() {
        imports.saveGames(List.of(response(81001L, "First")));
        em.flush();
        em.clear();
        Long id = games.findByIgdbId(81001L).orElseThrow().getId();

        imports.saveGames(List.of(new IgdbGameResponse(
                81001L, "Updated", "Summary", null, 0L, null,
                null, null, null, null)));
        em.flush();
        em.clear();

        assertThat(games.findByIgdbId(81001L).orElseThrow().getId()).isEqualTo(id);
        assertLinks(id);
    }

    private void assertLinks(Long gameId) {
        for (String entity : List.of("GameGenre", "GamePlatform", "GameSeriesGame")) {
            assertThat(em.createQuery("select count(l) from " + entity + " l where l.game.id = :id", Long.class)
                    .setParameter("id", gameId).getSingleResult()).as(entity).isEqualTo(1L);
        }
    }

    private long count(String entity) {
        return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }

    private IgdbGameResponse response(long id, String title) {
        return new IgdbGameResponse(id, title, "Summary", null, 0L, null, null,
                List.of(new NamedResource(82001L, "Action")),
                List.of(new NamedResource(83001L, "PC")),
                List.of(new NamedResource(84001L, "Shared series")));
    }
}
