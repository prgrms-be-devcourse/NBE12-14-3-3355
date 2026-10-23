package com.gamelog.nbe121423355.domain.usergame.entity

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Platform
import com.gamelog.nbe121423355.domain.user.entity.User
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.function.Consumer
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock

class UserGameMembershipTest {
    private val user = User("test", "test@example.com", "password")
    private val game = mock(Game::class.java)

    @Test
    @DisplayName("좋아요만 있는 게임은 생성·수정·상태 변경 시 라이브러리에 포함되지 않는다")
    fun likedOnlyIsExcludedAcrossCreationAndUpdate() {
        val record = UserGame(user, game, null, false, false, false, true,
            null, null, null, null, null, null, null)
        assertThat(record.inLibrary).isFalse()
        assertThat(record.liked).isTrue()
        record.updatePlayRecord(null, false, false, false, true,
            null, null, null, null, null, null, null)
        assertThat(record.inLibrary).isFalse()
        val quick = UserGame(user, game)
        quick.changeLiked(true)
        assertThat(quick.inLibrary).isFalse()
    }

    @ParameterizedTest
    @MethodSource("stateCycles")
    @DisplayName("마지막 상태를 해제하면 라이브러리에서 제외되고 좋아요는 유지된다")
    fun finalStateRemovalExcludesButKeepsLike(cycle: Consumer<UserGame>) {
        val record = UserGame(user, game)
        record.changeLiked(true)
        cycle.accept(record)
        assertThat(record.inLibrary).isFalse()
        assertThat(record.liked).isTrue()
        record.changeWishlist(true)
        assertThat(record.inLibrary).isTrue()
    }

    @ParameterizedTest
    @MethodSource("recordFields")
    @DisplayName("플랫폼·양수 시간·날짜 기록이 남아 있으면 라이브러리에 유지된다")
    fun anyRemainingRecordKeepsMembership(field: Int) {
        val record = UserGame(
            user = user,
            game = game,
            playStatus = null,
            playing = false,
            backlog = false,
            wishlist = false,
            liked = false,
            platform = if (field == 0) mock(Platform::class.java) else null,
            playTimeHours = if (field == 1) BigDecimal("0.1") else null,
            finishTimeHours = if (field == 2) BigDecimal.ONE else null,
            masterTimeHours = if (field == 3) BigDecimal.ONE else null,
            startedAt = if (field == 4) LocalDate.now() else null,
            completedAt = if (field == 5) LocalDate.now() else null,
            lastPlayedAt = if (field == 6) LocalDateTime.now() else null,
        )
        assertThat(record.inLibrary).isTrue()
        record.changePlaying(true)
        record.changePlaying(false)
        assertThat(record.inLibrary).isTrue()
        record.updatePlayRecord(null, false, false, false, false,
            null, null, null, null, null, null, null)
        assertThat(record.inLibrary).isFalse()
    }

    @ParameterizedTest
    @MethodSource("zeroHours")
    @DisplayName("0시간은 생성·수정·상태 변경 시 라이브러리 포함 조건에서 제외된다")
    fun zeroHoursDoNotCountAcrossCreationAndUpdate(zero: BigDecimal) {
        val record = UserGame(user, game, null, false, false, false, true,
            null, zero, zero, zero, null, null, null)
        assertThat(record.inLibrary).isFalse()
        record.changePlaying(true)
        assertThat(record.inLibrary).isTrue()
        record.changePlaying(false)
        assertThat(record.inLibrary).isFalse()
        record.updatePlayRecord(null, false, false, false, true,
            null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, null, null, null)
        assertThat(record.inLibrary).isTrue()
        record.updatePlayRecord(null, false, false, false, true,
            null, zero, zero, zero, null, null, null)
        assertThat(record.inLibrary).isFalse()
        assertThat(record.liked).isTrue()
    }

    companion object {
        @JvmStatic
        fun stateCycles(): Stream<Consumer<UserGame>> = Stream.of(
            Consumer { record ->
            record.changePlayStatus(PlayStatus.PLAYED)
            assertThat(record.inLibrary).isTrue()
            record.clearPlayStatus()
            },
            Consumer { record ->
            record.changePlaying(true)
            assertThat(record.inLibrary).isTrue()
            record.changePlaying(false)
            },
            Consumer { record ->
            record.changeWishlist(true)
            assertThat(record.inLibrary).isTrue()
            record.changeWishlist(false)
            },
            Consumer { record ->
            record.changeBacklog(true)
            assertThat(record.inLibrary).isTrue()
            record.changeBacklog(false)
            },
        )

        @JvmStatic
        fun recordFields(): Stream<Int> = Stream.of(0, 1, 2, 3, 4, 5, 6)

        @JvmStatic
        fun zeroHours(): Stream<BigDecimal> = Stream.of(BigDecimal.ZERO, BigDecimal("0.0"), BigDecimal("0.00"))
    }
}
