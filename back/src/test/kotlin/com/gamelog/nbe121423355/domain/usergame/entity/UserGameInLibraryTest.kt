package com.gamelog.nbe121423355.domain.usergame.entity

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Platform
import com.gamelog.nbe121423355.domain.user.entity.User
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class UserGameInLibraryTest {
    private val user = User("test", "test@example.com", "password")
    private val game = mock(Game::class.java)

    @Test
    @DisplayName("좋아요만 있는 게임은 라이브러리에 포함되지 않는다")
    fun likedOnlyIsExcludedFromLibrary() {
        val record = UserGame(user, game)

        record.changeLiked(true)

        assertThat(record.liked).isTrue()
        assertThat(record.inLibrary).isFalse()
    }

    @Test
    @DisplayName("마지막 플레이 상태를 해제하면 라이브러리에서 제외되고 좋아요는 유지된다")
    fun clearingPlayStatusExcludesButKeepsLike() {
        val record = UserGame(user, game)
        record.changeLiked(true)
        record.changePlayStatus(PlayStatus.PLAYED)
        assertThat(record.inLibrary).isTrue()

        record.clearPlayStatus()

        assertThat(record.inLibrary).isFalse()
        assertThat(record.liked).isTrue()
    }

    @Test
    @DisplayName("마지막 플레이 중 상태를 해제하면 라이브러리에서 제외되고 좋아요는 유지된다")
    fun clearingPlayingExcludesButKeepsLike() {
        val record = UserGame(user, game)
        record.changeLiked(true)
        record.changePlaying(true)
        assertThat(record.inLibrary).isTrue()

        record.changePlaying(false)

        assertThat(record.inLibrary).isFalse()
        assertThat(record.liked).isTrue()
    }

    @Test
    @DisplayName("마지막 위시리스트 상태를 해제하면 라이브러리에서 제외되고 좋아요는 유지된다")
    fun clearingWishlistExcludesButKeepsLike() {
        val record = UserGame(user, game)
        record.changeLiked(true)
        record.changeWishlist(true)
        assertThat(record.inLibrary).isTrue()

        record.changeWishlist(false)

        assertThat(record.inLibrary).isFalse()
        assertThat(record.liked).isTrue()
    }

    @Test
    @DisplayName("마지막 백로그 상태를 해제하면 라이브러리에서 제외되고 좋아요는 유지된다")
    fun clearingBacklogExcludesButKeepsLike() {
        val record = UserGame(user, game)
        record.changeLiked(true)
        record.changeBacklog(true)
        assertThat(record.inLibrary).isTrue()

        record.changeBacklog(false)

        assertThat(record.inLibrary).isFalse()
        assertThat(record.liked).isTrue()
    }

    @Test
    @DisplayName("플랫폼 기록이 남아 있으면 라이브러리에 유지된다")
    fun remainingPlatformKeepsLibrary() {
        val record = createRecord(platform = mock(Platform::class.java))
        assertRemainingRecordKeepsLibrary(record)
    }

    @Test
    @DisplayName("양수의 실제 플레이 시간이 남아 있으면 라이브러리에 유지된다")
    fun positivePlayTimeKeepsLibrary() {
        val record = createRecord(playTimeHours = BigDecimal("0.1"))
        assertRemainingRecordKeepsLibrary(record)
    }

    @Test
    @DisplayName("시작일 기록이 남아 있으면 라이브러리에 유지된다")
    fun remainingStartedAtKeepsLibrary() {
        val record = createRecord(startedAt = LocalDate.now())
        assertRemainingRecordKeepsLibrary(record)
    }

    @Test
    @DisplayName("완료일 기록이 남아 있으면 라이브러리에 유지된다")
    fun remainingCompletedAtKeepsLibrary() {
        val record = createRecord(completedAt = LocalDate.now())
        assertRemainingRecordKeepsLibrary(record)
    }

    @Test
    @DisplayName("마지막 플레이 일시 기록이 남아 있으면 라이브러리에 유지된다")
    fun remainingLastPlayedAtKeepsLibrary() {
        val record = createRecord(lastPlayedAt = LocalDateTime.now())
        assertRemainingRecordKeepsLibrary(record)
    }

    @Test
    @DisplayName("0시간은 생성·수정·상태 변경 시 라이브러리 포함 조건에서 제외된다")
    fun zeroHoursDoNotCountAcrossCreationAndUpdate() {
        assertZeroHoursDoNotCount(BigDecimal.ZERO)
    }

    @Test
    @DisplayName("0.0시간은 생성·수정·상태 변경 시 라이브러리 포함 조건에서 제외된다")
    fun zeroHoursWithOneDecimalDoNotCountAcrossCreationAndUpdate() {
        assertZeroHoursDoNotCount(BigDecimal("0.0"))
    }

    @Test
    @DisplayName("0.00시간은 생성·수정·상태 변경 시 라이브러리 포함 조건에서 제외된다")
    fun zeroHoursWithTwoDecimalsDoNotCountAcrossCreationAndUpdate() {
        assertZeroHoursDoNotCount(BigDecimal("0.00"))
    }

    private fun assertRemainingRecordKeepsLibrary(record: UserGame) {
        assertThat(record.inLibrary).isTrue()
        record.changePlaying(true)
        record.changePlaying(false)
        assertThat(record.inLibrary).isTrue()
        record.updatePlayRecord(null, false, false, false, false,
            null, null, null, null, null, null, null)
        assertThat(record.inLibrary).isFalse()
    }

    private fun assertZeroHoursDoNotCount(zero: BigDecimal) {
        val record = createRecord(
            liked = true,
            playTimeHours = zero,
            finishTimeHours = zero,
            masterTimeHours = zero,
        )
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

    private fun createRecord(
        liked: Boolean = false,
        platform: Platform? = null,
        playTimeHours: BigDecimal? = null,
        finishTimeHours: BigDecimal? = null,
        masterTimeHours: BigDecimal? = null,
        startedAt: LocalDate? = null,
        completedAt: LocalDate? = null,
        lastPlayedAt: LocalDateTime? = null,
    ): UserGame = UserGame(
        user = user,
        game = game,
        playStatus = null,
        playing = false,
        backlog = false,
        wishlist = false,
        liked = liked,
        platform = platform,
        playTimeHours = playTimeHours,
        finishTimeHours = finishTimeHours,
        masterTimeHours = masterTimeHours,
        startedAt = startedAt,
        completedAt = completedAt,
        lastPlayedAt = lastPlayedAt,
    )
}
