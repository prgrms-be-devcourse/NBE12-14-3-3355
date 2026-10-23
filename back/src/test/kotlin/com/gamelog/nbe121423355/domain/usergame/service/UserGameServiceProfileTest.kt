package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class UserGameServiceProfileTest {
    @InjectMocks
    private lateinit var userGameService: UserGameService

    @Mock
    private lateinit var userGameRepository: UserGameRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var gameRepository: GameRepository

    @Mock
    private lateinit var platformRepository: PlatformRepository

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    @Mock
    private lateinit var userFavoriteGameRepository: UserFavoriteGameRepository

    @Mock
    private lateinit var userFollowRepository: UserFollowRepository

    @Test
    @DisplayName("비로그인 사용자는 공개 프로필을 조회하고 팔로우 상태는 조회하지 않는다")
    fun anonymousProfile() {
        `when`(userRepository.existsByIdAndDeletedAtIsNull(2L)).thenReturn(true)
        `when`(reviewRepository.findAverageRating(2L)).thenReturn(BigDecimal.ZERO)

        val result = userGameService.profileTab(2L, null)
        assertThat(result.userId).isEqualTo(2L)
        assertThat(result.isMe).isFalse()
        assertThat(result.isFollowing).isFalse()
        verifyNoInteractions(userFollowRepository)
    }

    @Test
    @DisplayName("다른 사용자 프로필에서 현재 사용자의 팔로우 여부를 반환한다")
    fun otherUserProfile() {
        `when`(userRepository.existsByIdAndDeletedAtIsNull(2L)).thenReturn(true)
        `when`(reviewRepository.findAverageRating(2L)).thenReturn(BigDecimal.ZERO)
        `when`(userFollowRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(true)

        val result = userGameService.profileTab(2L, 1L)
        assertThat(result.isMe).isFalse()
        assertThat(result.isFollowing).isTrue()
        verify(userGameRepository).findPlayedGames(2L)
    }

    @Test
    @DisplayName("존재하지 않는 프로필은 조회할 수 없다")
    fun missingProfile() {
        val missingId = 999L
        assertThatThrownBy {
            userGameService.profileTab(missingId, null)
        }
            .isInstanceOf(ServiceException::class.java)
        verifyNoInteractions(userGameRepository, userFollowRepository)
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 공개 게임 목록은 조회할 수 없다")
    fun missingPublicGameList() {
        val missingUserId = 999L
        val request = UserGameSearchRequest()

        assertThatThrownBy {
            userGameService.getPublicUserGameList(
                missingUserId,
                request
            )
        }.isInstanceOf(ServiceException::class.java)
        verifyNoInteractions(userGameRepository)
    }

    @Test
    @DisplayName("프로필에 조회한 산점도 데이터를 그대로 반환한다")
    fun returnsScatterData() {
        val scatterData = listOf(
            UserGameScatterDto(1L, "Game 1", null, BigDecimal.TEN, BigDecimal("4.5")),
            UserGameScatterDto(2L, "Game 2", null, BigDecimal("20"), BigDecimal("3.5")),
        )
        mockProfileRepositories(scatterData = scatterData)

        val response = userGameService.profileTab(1L, 1L)

        assertThat(response.scatterData).containsExactlyElementsOf(scatterData)
    }

    @Test
    @DisplayName("장시간 플레이 비율이 40% 이상 70% 미만이면 골고루 즐긴다고 판단한다")
    fun mixedPlayTimes() {
        mockProfileRepositories(playedGames = listOf(
            createPlayedGame(hours = 30),
            createPlayedGame(hours = 50),
            createPlayedGame(hours = 10),
        ))

        val result = userGameService.profileTab(1L, 1L).tasteResponse.longPlay

        assertThat(result.ratio).isEqualByComparingTo("0.6667")
        assertThat(result.message).isEqualTo("장시간 플레이와 짧은 게임을 골고루 즐겨요.")
    }

    @Test
    @DisplayName("장시간 플레이 비율이 70% 이상이면 장시간 플레이 성향으로 판단한다")
    fun prefersLongGames() {
        mockProfileRepositories(playedGames = listOf(
            createPlayedGame(hours = 30),
            createPlayedGame(hours = 40),
            createPlayedGame(hours = 50),
        ))

        val result = userGameService.profileTab(1L, 1L).tasteResponse.longPlay

        assertThat(result.ratio).isEqualByComparingTo("1")
        assertThat(result.message).isEqualTo("장시간 플레이하는 게임이 많아요.")
    }

    @Test
    @DisplayName("플레이 시간 기록이 3개 미만이면 장시간 플레이 성향을 분석하지 않는다")
    fun insufficientPlayTimes() {
        mockProfileRepositories(playedGames = listOf(
            createPlayedGame(hours = 30),
            createPlayedGame(hours = 20),
        ))

        val result = userGameService.profileTab(1L, 1L).tasteResponse.longPlay

        assertThat(result.ratio).isNull()
        assertThat(result.message).isEqualTo("아직 플레이 기록이 부족해요.")
    }

    @Test
    @DisplayName("2.5 이상 4.0 미만 평점이 가장 많으면 중간 평점 성향으로 판단한다")
    fun prefersMediumRatings() {
        mockProfileRepositories(ratings = listOf(
            BigDecimal("3.0"), BigDecimal("3.5"), BigDecimal("4.5"), BigDecimal("2.0"),
        ))

        val result = userGameService.profileTab(1L, 1L).tasteResponse.rating

        assertThat(result.ratio).isEqualByComparingTo("0.5")
        assertThat(result.message).isEqualTo("게임을 비교적 후하게 평가하는 편이에요.")
    }

    @Test
    @DisplayName("높은 평점과 중간 평점이 동률이면 중간 평점 성향을 선택한다")
    fun tiedRatingsPreferMedium() {
        mockProfileRepositories(ratings = listOf(
            BigDecimal("4.0"), BigDecimal("4.5"), BigDecimal("3.0"), BigDecimal("3.5"),
        ))

        val result = userGameService.profileTab(1L, 1L).tasteResponse.rating

        assertThat(result.message).isEqualTo("게임을 비교적 후하게 평가하는 편이에요.")
    }

    @Test
    @DisplayName("완료 비율이 40% 이상 70% 미만이면 절반 정도를 완료했다고 판단한다")
    fun mediumCompletionRate() {
        mockProfileRepositories(playedGames = listOf(
            createPlayedGame(status = PlayStatus.COMPLETED),
            createPlayedGame(status = PlayStatus.COMPLETED),
            createPlayedGame(status = PlayStatus.PLAYED),
        ))

        val result = userGameService.profileTab(1L, 1L).tasteResponse.completion

        assertThat(result.ratio).isEqualByComparingTo("0.6667")
        assertThat(result.message).isEqualTo("플레이한 게임 중 절반 정도를 완료했어요.")
    }

    @Test
    @DisplayName("장르별 게임 수를 기준으로 장르 비율을 계산한다")
    fun calculatesGenreRatios() {
        mockProfileRepositories(genres = listOf(
            UserGameGenreDTO(1L, "RPG", 2L),
            UserGameGenreDTO(2L, "Action", 1L),
        ))

        val result = userGameService.profileTab(1L, 1L).genreDistribution

        assertThat(result.map { it.genreName }).containsExactly("RPG", "Action")
        assertThat(result[0].ratio).isEqualByComparingTo("0.6667")
        assertThat(result[1].ratio).isEqualByComparingTo("0.3333")
    }

    @Test
    @DisplayName("장르 데이터가 없으면 빈 목록을 반환한다")
    fun emptyGenreDistribution() {
        mockProfileRepositories()

        val response = userGameService.profileTab(1L, 1L)

        assertThat(response.genreDistribution).isEmpty()
    }

    private fun mockProfileRepositories(
        userId: Long = 1L,
        playedGames: List<UserGame> = emptyList(),
        ratings: List<BigDecimal> = emptyList(),
        scatterData: List<UserGameScatterDto> = emptyList(),
        genres: List<UserGameGenreDTO> = emptyList(),
    ) {
        `when`(userRepository.existsByIdAndDeletedAtIsNull(userId)).thenReturn(true)
        `when`(userGameRepository.findPlayedGames(userId)).thenReturn(playedGames)
        `when`(reviewRepository.findPlayedGameRatings(userId)).thenReturn(ratings.toMutableList())
        `when`(reviewRepository.findAverageRating(userId)).thenReturn(BigDecimal.ZERO)
        `when`(userGameRepository.findPlayedGameScatterData(userId)).thenReturn(scatterData)
        `when`(userGameRepository.findGenreDistribution(userId)).thenReturn(genres)
    }

    private fun createPlayedGame(hours: Long? = null, status: PlayStatus? = null): UserGame =
        mock(UserGame::class.java).apply {
            if (hours != null) `when`(playTimeHours).thenReturn(BigDecimal.valueOf(hours))
            if (status != null) `when`(playStatus).thenReturn(status)
        }
}
