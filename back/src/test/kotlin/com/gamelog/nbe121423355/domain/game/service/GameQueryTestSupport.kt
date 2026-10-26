package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
abstract class GameQueryTestSupport {

    @Autowired protected lateinit var gameService: GameService

    @Autowired protected lateinit var entityManager: EntityManager

    @Autowired protected lateinit var mockMvc: MockMvc

    protected lateinit var rpg: Genre
    protected lateinit var action: Genre
    protected lateinit var pc: Platform
    protected lateinit var console: Platform

    @BeforeEach
    fun setUp() {
        rpg = Genre.createFromIgdb(9001L, "Filter RPG")
        action = Genre.createFromIgdb(9002L, "Filter Action")
        pc = Platform.createFromIgdb(9001L, "Filter PC")
        console = Platform.createFromIgdb(9002L, "Filter Console")
        entityManager.persist(rpg)
        entityManager.persist(action)
        entityManager.persist(pc)
        entityManager.persist(console)

        saveGame(9001L, "Zelda Both", listOf(rpg, action), listOf(pc, console))
        saveGame(9002L, "Zelda RPG", listOf(rpg), listOf(console))
        saveGame(9003L, "Mario Action", listOf(action), listOf(pc))
        saveGame(9004L, "Unclassified", listOf(), listOf())
        entityManager.flush()
        entityManager.clear()
    }

    protected fun saveGame(
        igdbId: Long,
        title: String,
        genres: List<Genre>,
        platforms: List<Platform>,
    ) {
        val game =
            Game.createFromIgdb(
                IgdbGameResponse(
                    id = igdbId,
                    name = title,
                    summary = null,
                    cover = null,
                    first_release_date = 1704067200L,
                    rating = null,
                    involved_companies = mutableListOf(),
                    genres = mutableListOf(),
                    platforms = mutableListOf(),
                    collections = mutableListOf(),
                )
            )
        entityManager.persist(game)
        genres.forEach { genre -> entityManager.persist(GameGenre(game, genre)) }
        platforms.forEach { platform -> entityManager.persist(GamePlatform(game, platform)) }
    }
}
