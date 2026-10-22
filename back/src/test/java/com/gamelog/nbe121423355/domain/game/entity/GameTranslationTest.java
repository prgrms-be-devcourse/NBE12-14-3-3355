package com.gamelog.nbe121423355.domain.game.entity;

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse;
import com.gamelog.nbe121423355.domain.game.service.GameTranslationService;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class GameTranslationTest {
    @Autowired EntityManager em;

    private Game savedGame() {
        Game game = Game.createFromIgdb(response("Original"));
        em.persist(game);
        return reload(game);
    }

    private Game reload(Game game) {
        Long id = game.getId();
        em.flush();
        em.clear();
        return em.find(Game.class, id);
    }

    private IgdbGameResponse response(String text) {
        return new IgdbGameResponse(1L, "Game", text, null, null, null, null, null, null, null);
    }

    @Test
    void preservesKoreanOnReimportAndDetectsChangedSource() {
        Game game = savedGame();
        String hash = GameTranslationService.hash("Original");
        game.applyDescriptionTranslation("Original", "한국어 설명", hash);
        game = reload(game);
        game.updateFromIgdb(response("Changed"));
        game = reload(game);
        assertEquals("한국어 설명", game.getDescription());
        assertEquals("Changed", game.getDescriptionSource());
        assertEquals(hash, game.getDescriptionTranslatedHash());
        assertNotEquals(GameTranslationService.hash("Changed"), game.getDescriptionTranslatedHash());
    }

    @Test
    void unchangedSourceRemainsTranslated() {
        Game game = savedGame();
        game.applyDescriptionTranslation("Original", "한국어", GameTranslationService.hash("Original"));
        game = reload(game);
        game.updateFromIgdb(response("Original"));
        game = reload(game);
        assertEquals(GameTranslationService.hash(game.prepareDescriptionTranslation()), game.getDescriptionTranslatedHash());
        assertEquals("한국어", game.getDescription());
    }

    @Test
    void rejectsStaleOrEmptyTranslation() {
        Game game = savedGame();
        assertThrows(IllegalStateException.class, () -> game.applyDescriptionTranslation("Old", "한국어", "hash"));
        assertThrows(IllegalArgumentException.class, () -> game.applyDescriptionTranslation("Original", " ", "hash"));
        assertEquals("Original", reload(game).getDescription());
    }

    @Test
    void missingSummaryDoesNotOverwriteOriginalBackup() {
        Game game = savedGame();
        game.applyDescriptionTranslation("Original", "한국어", GameTranslationService.hash("Original"));
        game = reload(game);
        game.updateFromIgdb(response(null));
        game = reload(game);
        assertEquals("Original", game.prepareDescriptionTranslation());
        assertEquals("한국어", game.getDescription());
    }
}
