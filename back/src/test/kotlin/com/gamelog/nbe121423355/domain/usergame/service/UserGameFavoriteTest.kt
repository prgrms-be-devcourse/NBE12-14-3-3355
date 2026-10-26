package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameReqBody
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class UserGameFavoriteTest {
    @Autowired private lateinit var service: UserGameService
    @Autowired private lateinit var profileService: UserGameProfileService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var games: GameRepository
    @Autowired private lateinit var records: UserGameRepository
    @Autowired private lateinit var favorites: UserFavoriteGameRepository
    @Autowired private lateinit var em: EntityManager
    private lateinit var user: User
    private lateinit var game: Game

    @BeforeEach
    fun setUp() {
        user = users.save(User("membership", "membership@example.com", "password"))
        game = games.save(Game.createFromIgdb(IgdbGameResponse(987654L, "membership", "test",
            null, null, null, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())))
    }

    private fun request(hours: BigDecimal?, playing: Boolean = false) = UserGameReqBody(
        playStatus = null,
        isPlaying = playing,
        isBacklog = false,
        isWishlist = false,
        isLiked = true,
        platformId = null,
        playTimeHours = hours,
        finishTimeHours = null,
        masterTimeHours = null,
        startedAt = null,
        completedAt = null,
        lastPlayedAt = null,
    )

    @ParameterizedTest
    @ValueSource(strings = ["played", "playing", "wishlist", "backlog", "update", "upsert", "liked"])
    @DisplayName("라이브러리에서 제외되면 인생게임 지정만 해제되고 게임 기록과 좋아요는 유지된다")
    fun removalClearsFavoriteAndKeepsRecord(route: String) {
        val record = UserGame(user, game)
        record.changeLiked(true)
        when (route) {
            "played" -> record.changePlayStatus(PlayStatus.PLAYED)
            "wishlist" -> record.changeWishlist(true)
            "backlog" -> record.changeBacklog(true)
            "liked" -> Unit // 기존에 잘못 등록된 좋아요 전용 인생 게임도 정리한다.
            else -> record.changePlaying(true)
        }
        records.saveAndFlush(record)
        favorites.saveAndFlush(UserFavoriteGame(user, game, 1))
        val uid = checkNotNull(user.id)
        val gid = checkNotNull(game.id)
        val recordId = checkNotNull(record.id)
        when (route) {
            "played" -> service.changePlayed(uid, gid, null)
            "playing" -> assertThat(service.changePlaying(uid, gid, false)).isFalse()
            "wishlist" -> assertThat(service.changeWishlist(uid, gid, false)).isFalse()
            "backlog" -> assertThat(service.changeBacklog(uid, gid, false)).isFalse()
            "update" -> service.updatePlayRecord(uid, gid, request(null))
            "upsert" -> service.addOrUpdateGameToLibrary(uid, gid, request(null))
            "liked" -> service.changeLiked(uid, gid, true)
        }
        em.flush()
        em.clear()
        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(uid)).isEmpty()
        val saved = checkNotNull(records.findByUser_IdAndGame_Id(uid, gid))
        assertThat(saved.id).isEqualTo(recordId)
        assertThat(saved.inLibrary).isFalse()
        assertThat(saved.liked).isTrue()
    }

    @Test
    @DisplayName("플레이 시간을 0으로 바꾸고 다른 기록이 없으면 인생게임 지정이 해제된다")
    fun resettingHoursToZeroRemovesFavoriteButKeepsRecord() {
        val uid = checkNotNull(user.id)
        val gid = checkNotNull(game.id)
        val record = service.addOrUpdateGameToLibrary(uid, gid, request(BigDecimal.TEN)).userGame

        favorites.saveAndFlush(UserFavoriteGame(user, game, 1))

        val recordId = checkNotNull(record.id)

        service.updatePlayRecord(uid, gid, request(BigDecimal("0.0")))
        em.flush()
        em.clear()

        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(uid)).isEmpty()

        val saved = checkNotNull(records.findByUser_IdAndGame_Id(uid, gid))

        assertThat(saved.id).isEqualTo(recordId)
        assertThat(saved.inLibrary).isFalse()
        assertThat(saved.playTimeHours).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    @DisplayName("플레이 중을 해제해도 양수의 플레이 시간이 남아 있으면 인생게임이 유지된다")
    fun remainingHoursKeepFavorite() {
        val uid = checkNotNull(user.id)
        val gid = checkNotNull(game.id)
        val record = service.addOrUpdateGameToLibrary(
            uid, gid, request(BigDecimal.TEN, playing = true),
        ).userGame

        assertThat(record.playing).isTrue()
        favorites.saveAndFlush(UserFavoriteGame(user, game, 1))

        service.changePlaying(uid, gid, false)
        em.flush()
        em.clear()

        val saved = checkNotNull(records.findByUser_IdAndGame_Id(uid, gid))

        assertThat(saved.playing).isFalse()
        assertThat(saved.playTimeHours).isEqualByComparingTo(BigDecimal.TEN)
        assertThat(saved.inLibrary).isTrue()
        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(uid).map { it.game.id })
            .containsExactly(gid)
    }

    @Test
    @DisplayName("라이브러리 밖 게임의 인생게임 등록을 거절해도 기존 목록은 유지된다")
    fun rejectOutsideLibraryWithoutReplacingFavorites() {
        records.saveAndFlush(UserGame(user, game))
        val other = games.save(Game.createFromIgdb(IgdbGameResponse(987655L, "other", "test",
            null, null, null, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())))
        val eligible = UserGame(user, other)

        eligible.changeWishlist(true)
        records.saveAndFlush(eligible)

        val uid = checkNotNull(user.id)
        val otherId = checkNotNull(other.id)

        profileService.updateFavoriteGames(uid, listOf(otherId))

        assertThatThrownBy { profileService.updateFavoriteGames(uid, listOf(checkNotNull(game.id))) }
            .isInstanceOfSatisfying(ServiceException::class.java) { error ->
                assertThat(error.resultCode).isEqualTo("400-11")
                assertThat(error.msg).isEqualTo("라이브러리에 없는 게임이 포함되어 있습니다. 선택 목록을 확인해 주세요.")
            }
        em.flush()
        em.clear()

        assertThat(favorites.findAllByUserIdOrderByDisplayOrderAsc(uid).map { it.game.id })
            .containsExactly(otherId)
    }
}
