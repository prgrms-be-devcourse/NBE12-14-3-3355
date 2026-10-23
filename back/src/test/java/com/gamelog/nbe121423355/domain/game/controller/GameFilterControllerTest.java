package com.gamelog.nbe121423355.domain.game.controller;

import com.gamelog.nbe121423355.domain.game.entity.Genre;
import com.gamelog.nbe121423355.domain.game.entity.Platform;
import com.gamelog.nbe121423355.domain.game.repository.GenreRepository;
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class GameFilterControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private GenreRepository genres;
    @Autowired private PlatformRepository platforms;

    @Test
    void returnsEmptyArraysWhenNoFiltersExist() throws Exception {
        mockMvc.perform(get("/api/v1/games/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.genres").isArray())
                .andExpect(jsonPath("$.data.genres").isEmpty())
                .andExpect(jsonPath("$.data.platforms").isArray())
                .andExpect(jsonPath("$.data.platforms").isEmpty());
    }

    @Test
    void sortsBothFilterListsByNameRegardlessOfInsertionOrder() throws Exception {
        genres.save(Genre.createFromIgdb(80001L, "Zulu"));
        genres.save(Genre.createFromIgdb(80002L, "Alpha"));
        platforms.save(Platform.createFromIgdb(80001L, "Zulu"));
        platforms.save(Platform.createFromIgdb(80002L, "Alpha"));
        genres.flush();
        platforms.flush();

        mockMvc.perform(get("/api/v1/games/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.genres[*].name", contains("Alpha", "Zulu")))
                .andExpect(jsonPath("$.data.platforms[*].name", contains("Alpha", "Zulu")));
    }

    @Test
    void returnsPersistedFilterIdsWithoutAuthentication() throws Exception {
        Genre genre = genres.save(Genre.createFromIgdb(987654L, "Filter test RPG"));
        Platform platform = platforms.save(Platform.createFromIgdb(987654L, "Filter test PC"));
        mockMvc.perform(get("/api/v1/games/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.genres[*].id", hasItem(genre.getId().intValue())))
                .andExpect(jsonPath("$.data.genres[*].name", hasItem("Filter test RPG")))
                .andExpect(jsonPath("$.data.platforms[*].id", hasItem(platform.getId().intValue())))
                .andExpect(jsonPath("$.data.platforms[*].name", hasItem("Filter test PC")));
    }
}
