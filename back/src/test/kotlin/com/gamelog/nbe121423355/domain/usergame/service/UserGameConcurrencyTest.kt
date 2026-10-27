package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.domain.usergame.support.assertMySqlDuplicateEntry
import com.gamelog.nbe121423355.domain.usergame.support.recordRequest
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
@MySqlIntegrationTest
class UserGameConcurrencyTest {
    @Autowired private lateinit var userGameService: UserGameService
    @Autowired private lateinit var userGameRepository: UserGameRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var gameRepository: GameRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var userId: Long? = null
    private var gameId: Long? = null

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(User("테스트", "test@test.com", "password"))
        val game = gameRepository.save(Game.createFromIgdb(
            IgdbGameResponse(1L, "테스트 게임", "설명", null, null, null,
                mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf()),
        ))
        userId = checkNotNull(user.id)
        gameId = checkNotNull(game.id)
    }

    @Test
    @DisplayName("MySQL에서 같은 게임을 동시에 등록해도 사용자별 게임 기록은 한 건만 남는다")
    fun concurrentRegistrationKeepsOneRecord() {
        val userId = checkNotNull(userId)
        val gameId = checkNotNull(gameId)
        val reqBody = recordRequest(
            hours = BigDecimal("42.5"),
            finishHours = BigDecimal("35.0"),
            masterHours = BigDecimal("80.0"),
            startedAt = LocalDate.of(2026, 8, 1),
            completedAt = LocalDate.of(2026, 8, 20),
            lastPlayedAt = LocalDateTime.of(2026, 8, 20, 21, 30),
        )
        val threadCount = 2
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        var successCount = 0

        try {
            val requests = List(threadCount) {
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS)) { "동시 등록 시작 대기 시간이 초과되었습니다." }
                    userGameService.addOrUpdateGameToLibrary(userId, gameId, reqBody)
                    Unit
                })
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            for (request in requests) {
                try {
                    request.get(30, TimeUnit.SECONDS)
                    successCount++
                } catch (exception: ExecutionException) {
                    // 두 요청이 모두 미등록으로 조회한 경우의 중복 INSERT 실패만 허용한다.
                    checkNotNull(exception.cause).assertMySqlDuplicateEntry()
                }
            }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        }

        // 나중 요청이 생성된 행을 조회해 UPDATE하면 두 요청 모두 성공할 수 있다.
        assertThat(successCount).isBetween(1, threadCount)
        assertThat(recordCount()).isEqualTo(1L)
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
