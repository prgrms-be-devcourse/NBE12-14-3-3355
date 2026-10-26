package com.gamelog.nbe121423355.domain.usergame.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.*
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyList
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest

@ExtendWith(MockitoExtension::class)
class UserGameProfileServiceTest {
    @Mock
    private lateinit var userGameService: UserGameService

    @InjectMocks
    private lateinit var userGameProfileService: UserGameProfileService

    @Mock
    private lateinit var userGameRepository: UserGameRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var reviewRepository: ReviewRepository

    @Mock
    private lateinit var userFavoriteGameRepository: UserFavoriteGameRepository

    @Mock
    private lateinit var userFollowRepository: UserFollowRepository

    @Nested
    @DisplayName("프로필 조회와 통계")
    inner class ProfileStatistics {
        @Test
        @DisplayName("비로그인 사용자는 공개 프로필을 조회하고 팔로우 상태는 조회하지 않는다")
        fun anonymousProfile() {
            `when`(userRepository.existsByIdAndDeletedAtIsNull(2L)).thenReturn(true)
            `when`(reviewRepository.findAverageRating(2L)).thenReturn(BigDecimal.ZERO)

            val result = userGameProfileService.profileTab(2L, null)
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

            val result = userGameProfileService.profileTab(2L, 1L)
            assertThat(result.isMe).isFalse()
            assertThat(result.isFollowing).isTrue()
            verify(userGameRepository).findPlayedGames(2L)
        }

        @Test
        @DisplayName("존재하지 않는 프로필은 조회할 수 없다")
        fun missingProfile() {
            val missingId = 999L
            assertThatThrownBy {
                userGameProfileService.profileTab(missingId, null)
            }
                .isInstanceOf(ServiceException::class.java)
            verifyNoInteractions(userGameRepository, userFollowRepository)
        }

        @Test
        @DisplayName("프로필에 조회한 산점도 데이터를 그대로 반환한다")
        fun returnsScatterData() {
            val scatterData = listOf(
                UserGameScatterDto(1L, "Game 1", null, BigDecimal.TEN, BigDecimal("4.5")),
                UserGameScatterDto(2L, "Game 2", null, BigDecimal("20"), BigDecimal("3.5")),
            )
            mockProfileRepositories(scatterData = scatterData)

            val response = userGameProfileService.profileTab(1L, 1L)

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

            val result = userGameProfileService.profileTab(1L, 1L).tasteResponse.longPlay

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

            val result = userGameProfileService.profileTab(1L, 1L).tasteResponse.longPlay

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

            val result = userGameProfileService.profileTab(1L, 1L).tasteResponse.longPlay

            assertThat(result.ratio).isNull()
            assertThat(result.message).isEqualTo("아직 플레이 기록이 부족해요.")
        }

        @Test
        @DisplayName("2.5 이상 4.0 미만 평점이 가장 많으면 중간 평점 성향으로 판단한다")
        fun prefersMediumRatings() {
            mockProfileRepositories(ratings = listOf(
                BigDecimal("3.0"), BigDecimal("3.5"), BigDecimal("4.5"), BigDecimal("2.0"),
            ))

            val result = userGameProfileService.profileTab(1L, 1L).tasteResponse.rating

            assertThat(result.ratio).isEqualByComparingTo("0.5")
            assertThat(result.message).isEqualTo("게임을 비교적 후하게 평가하는 편이에요.")
        }

        @Test
        @DisplayName("높은 평점과 중간 평점이 동률이면 중간 평점 성향을 선택한다")
        fun tiedRatingsPreferMedium() {
            mockProfileRepositories(ratings = listOf(
                BigDecimal("4.0"), BigDecimal("4.5"), BigDecimal("3.0"), BigDecimal("3.5"),
            ))

            val result = userGameProfileService.profileTab(1L, 1L).tasteResponse.rating

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

            val result = userGameProfileService.profileTab(1L, 1L).tasteResponse.completion

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

            val result = userGameProfileService.profileTab(1L, 1L).genreDistribution

            assertThat(result.map { it.genreName }).containsExactly("RPG", "Action")
            assertThat(result[0].ratio).isEqualByComparingTo("0.6667")
            assertThat(result[1].ratio).isEqualByComparingTo("0.3333")
        }

        @Test
        @DisplayName("장르 데이터가 없으면 빈 목록을 반환한다")
        fun emptyGenreDistribution() {
            mockProfileRepositories()

            val response = userGameProfileService.profileTab(1L, 1L)

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

    @Nested
    @DisplayName("최근 리뷰 조회")
    inner class RecentReviews {
        @Test
        @DisplayName("최근 작성한 리뷰를 최대 3개 요청하고 조회 결과를 반환한다")
        fun getRecentReviews() {
            val userId = 1L
            val pageable = PageRequest.of(0, 3)
            val modifiedAt = LocalDateTime.of(2026, 10, 22, 12, 0)
            val review1 = createReview(1L, createUserGame(10L, "Game 1", "cover1"), modifiedAt)
            val review2 = createReview(2L, createUserGame(20L, "Game 2", "cover2"), modifiedAt.minusDays(1))
            given(reviewRepository.findRecentReviews(userId, pageable))
                .willReturn(mutableListOf(review1, review2))

            val result = userGameProfileService.getRecentReviews(userId)

            assertThat(result).containsExactly(
                RecentReviewResponse(
                    reviewId = 1L,
                    gameId = 10L,
                    gameTitle = "Game 1",
                    gameCoverImageUrl = "cover1",
                    playStatus = null,
                    platform = null,
                    rating = null,
                    content = null,
                    lastModifiedDate = modifiedAt,
                ),
                RecentReviewResponse(
                    reviewId = 2L,
                    gameId = 20L,
                    gameTitle = "Game 2",
                    gameCoverImageUrl = "cover2",
                    playStatus = null,
                    platform = null,
                    rating = null,
                    content = null,
                    lastModifiedDate = modifiedAt.minusDays(1),
                ),
            )
            verify(reviewRepository).findRecentReviews(userId, pageable)
        }

        private fun createUserGame(gameId: Long, title: String, cover: String): UserGame {
            val game = mock(Game::class.java)
            given(game.id).willReturn(gameId)
            given(game.title).willReturn(title)
            given(game.coverImageUrl).willReturn(cover)

            return mock(UserGame::class.java).apply {
                given(this.game).willReturn(game)
            }
        }

        private fun createReview(id: Long, userGame: UserGame, modifiedAt: LocalDateTime): Review =
            mock(Review::class.java).apply {
                given(this.id).willReturn(id)
                given(this.userGame).willReturn(userGame)
                given(lastModifiedDate).willReturn(modifiedAt)
            }
    }

    @Nested
    @DisplayName("인생 게임 수정")
    inner class FavoriteGames {
        @Test
        @DisplayName("인생게임을 요청한 순서대로 저장하고 표시 순서와 함께 반환한다")
        fun updateFavoriteGames() {
            val userId = 1L
            val gameIds = listOf(20L, 10L)
            val user = mock(User::class.java)
            val game1 = mock(Game::class.java)
            val game2 = mock(Game::class.java)
            val userGame1 = mock(UserGame::class.java)
            val userGame2 = mock(UserGame::class.java)
            var savedGames = emptyList<UserFavoriteGame>()

            given(userRepository.findById(userId)).willReturn(Optional.of(user))
            given(user.id).willReturn(userId)
            given(game1.id).willReturn(10L)
            given(game2.id).willReturn(20L)
            given(game1.title).willReturn("Game 1")
            given(game2.title).willReturn("Game 2")
            given(userGame1.game).willReturn(game1)
            given(userGame2.game).willReturn(game2)
            given(userGameRepository.findAllByUser_IdAndGame_IdInAndInLibraryTrue(userId, gameIds))
                .willReturn(listOf(userGame1, userGame2))
            given(userFavoriteGameRepository.saveAll(anyList<UserFavoriteGame>()))
                .willAnswer { invocation ->
                    savedGames = invocation.getArgument<List<UserFavoriteGame>>(0)
                    savedGames
                }

            val result = userGameProfileService.updateFavoriteGames(userId, gameIds)

            verify(userFavoriteGameRepository).deleteAllByUserId(userId)
            assertThat(savedGames.map { it.game.id }).containsExactly(20L, 10L)
            assertThat(savedGames.map { it.displayOrder }).containsExactly(1, 2)
            assertThat(result).containsExactly(
                UserFavoriteGameResponse(
                    gameId = 20L,
                    title = "Game 2",
                    coverImageUrl = null,
                    displayOrder = 1,
                ),
                UserFavoriteGameResponse(
                    gameId = 10L,
                    title = "Game 1",
                    coverImageUrl = null,
                    displayOrder = 2,
                ),
            )
        }

        @Test
        @DisplayName("인생게임은 최대 5개까지 등록할 수 있다")
        fun rejectMoreThanFiveFavoriteGames() {
            val gameIds: List<Long> = listOf(1L, 2L, 3L, 4L, 5L, 6L)

            assertThatThrownBy {
                userGameProfileService.updateFavoriteGames(1L, gameIds)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("인생게임에는 같은 게임을 중복 등록할 수 없다")
        fun rejectDuplicateFavoriteGames() {
            val gameIds: List<Long> = listOf(10L, 20L, 10L)

            assertThatThrownBy {
                userGameProfileService.updateFavoriteGames(1L, gameIds)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("인생게임 목록에 빈 리스트를 전달하면 기존 인생게임을 모두 삭제한다")
        fun clearFavoriteGames() {
            val userId = 1L

            val user = mock(User::class.java)

            given(userRepository.findById(userId))
                .willReturn(Optional.of(user))

            val result: List<UserFavoriteGameResponse> = userGameProfileService.updateFavoriteGames(
                userId,
                listOf()
            )

            verify(userFavoriteGameRepository)
                .deleteAllByUserId(userId)

            verify(userFavoriteGameRepository)
                .saveAll(anyList())

            assertThat(result).isEmpty()
        }
    }
}
