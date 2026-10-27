package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserPreferenceGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class UserPreferenceGameServiceTest
@Autowired
constructor(
    private val userPreferenceGameService: UserPreferenceGameService,
    private val userPreferenceGameRepository: UserPreferenceGameRepository,
    private val userRepository: UserRepository,
    private val gameRepository: GameRepository,
) {
    private val igdbIdSeq = AtomicLong(1L)

    private val User.requiredId: Long get() = checkNotNull(id)
    private val Game.requiredId: Long get() = checkNotNull(id)

    private fun saveUser(): User {
        return userRepository.save(User("테스트", "test@test.com", "password"))
    }

    private fun saveGame(title: String): Game {
        val response = IgdbGameResponse(
            igdbIdSeq.getAndIncrement(),
            title,
            "테스트 게임 설명",
            null,
            1704067200L,
            BigDecimal("90.50"),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
            mutableListOf(),
        )
        return gameRepository.save(Game.createFromIgdb(response))
    }

    @Test
    @DisplayName("선호 게임 저장 성공")
    fun updatePreferredGames_success() {
        val user = saveUser()
        val witcher = saveGame("The Witcher 3")
        val elden = saveGame("Elden Ring")

        val result = userPreferenceGameService.updatePreferredGames(user.requiredId, listOf(witcher.requiredId, elden.requiredId))

        assertThat(result).hasSize(2)
        assertThat(result.map { it.gameTitle })
            .containsExactlyInAnyOrder("The Witcher 3", "Elden Ring")
        assertThat(userPreferenceGameRepository.findByUser_Id(user.id)).hasSize(2)
    }

    @Test
    @DisplayName("선호 게임 재저장 시 기존 선택이 교체된다")
    fun updatePreferredGames_replacesExisting() {
        val user = saveUser()
        val witcher = saveGame("The Witcher 3")
        val elden = saveGame("Elden Ring")
        userPreferenceGameService.updatePreferredGames(user.requiredId, listOf(witcher.requiredId))

        val result = userPreferenceGameService.updatePreferredGames(user.requiredId, listOf(elden.requiredId))

        assertThat(result.map { it.gameTitle }).containsExactly("Elden Ring")
        assertThat(userPreferenceGameRepository.findByUser_Id(user.id)).hasSize(1)
    }

    @Test
    @DisplayName("선호 게임 저장 실패 - 최대 3개 초과")
    fun updatePreferredGames_fail_tooMany() {
        val user = saveUser()
        val g1 = saveGame("게임1")
        val g2 = saveGame("게임2")
        val g3 = saveGame("게임3")
        val g4 = saveGame("게임4")

        assertThatThrownBy {
            userPreferenceGameService.updatePreferredGames(
                user.requiredId, listOf(g1.requiredId, g2.requiredId, g3.requiredId, g4.requiredId),
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("400-12") })
    }

    @Test
    @DisplayName("선호 게임 저장 실패 - 존재하지 않는 유저")
    fun updatePreferredGames_fail_userNotFound() {
        val game = saveGame("The Witcher 3")

        assertThatThrownBy { userPreferenceGameService.updatePreferredGames(999_999L, listOf(game.requiredId)) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("404-1") })
    }

    @Test
    @DisplayName("선호 게임 저장 실패 - 존재하지 않는 게임 포함")
    fun updatePreferredGames_fail_nonexistentGame() {
        val user = saveUser()
        val witcher = saveGame("The Witcher 3")

        assertThatThrownBy {
            userPreferenceGameService.updatePreferredGames(user.requiredId, listOf(witcher.requiredId, 999_999L))
        }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("400-11") })
    }

    @Test
    @DisplayName("선호 게임 조회 성공")
    fun getPreferredGames_success() {
        val user = saveUser()
        val witcher = saveGame("The Witcher 3")
        userPreferenceGameService.updatePreferredGames(user.requiredId, listOf(witcher.requiredId))

        val result = userPreferenceGameService.getPreferredGames(user.id)

        assertThat(result).hasSize(1)
        assertThat(result[0].gameId).isEqualTo(witcher.id)
        assertThat(result[0].gameTitle).isEqualTo("The Witcher 3")
    }

    @Test
    @DisplayName("선호 게임 조회 - 선택한 적 없으면 빈 리스트")
    fun getPreferredGames_empty() {
        val user = saveUser()

        val result = userPreferenceGameService.getPreferredGames(user.id)

        assertThat(result).isEmpty()
    }
}
