package com.gamelog.nbe121423355.domain.usergame.repository

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.sql.SQLException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
@MySqlIntegrationTest
class UserGameRepositoryTest {
    @Autowired private lateinit var userGameRepository: UserGameRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var gameRepository: GameRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var userId: Long? = null
    private var gameId: Long? = null

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("테스트", "repository@test.com", "password"))
        val game = gameRepository.save(Game.createFromIgdb(
            IgdbGameResponse(1L, "테스트 게임", "설명", null, null, null,
                mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()),
        ))
        userId = checkNotNull(user.id)
        gameId = checkNotNull(game.id)
    }

    @Test
    @DisplayName("MySQL UNIQUE 제약조건은 같은 사용자와 게임의 중복 행 저장을 거절한다")
    fun uniqueConstraintRejectsDuplicateRecord() {
        val user = userRepository.findById(checkNotNull(userId)).orElseThrow()
        val game = gameRepository.findById(checkNotNull(gameId)).orElseThrow()
        userGameRepository.saveAndFlush(UserGame(user, game))

        assertThatThrownBy { userGameRepository.saveAndFlush(UserGame(user, game)) }
            .satisfies(::assertDuplicateEntry)
        assertThat(recordCount()).isEqualTo(1L)
    }

    private fun assertDuplicateEntry(failure: Throwable) {
        assertThat(failure).isInstanceOf(DataIntegrityViolationException::class.java)
        var rootCause = failure
        while (true) {
            rootCause = rootCause.cause ?: break
        }
        assertThat(rootCause).isInstanceOfSatisfying(SQLException::class.java) { sqlException ->
            assertThat(sqlException.errorCode).isEqualTo(1062)
            assertThat(sqlException.sqlState).isEqualTo("23000")
        }
    }

    private fun recordCount(): Long = checkNotNull(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_games WHERE user_id = ? AND game_id = ?",
        Long::class.javaObjectType, userId, gameId,
    ))

    @AfterEach
    fun tearDown() {
        val userId = userId
        val gameId = gameId
        if (userId != null && gameId != null) {
            userGameRepository.findByUser_IdAndGame_Id(userId, gameId)?.let(userGameRepository::delete)
        }
        userId?.let(userRepository::deleteById)
        gameId?.let(gameRepository::deleteById)
    }
}
