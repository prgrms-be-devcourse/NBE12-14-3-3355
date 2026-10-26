package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGame
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGenre
import com.gamelog.nbe121423355.domain.usergame.entity.PlayStatus
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
open class PersonalizedGameRecommendationServiceTest {

    @Autowired
    private lateinit var recommendationService: PersonalizedGameRecommendationService

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    @DisplayName("여러 기록 게임에서 같은 후보가 추천되면 순위 점수를 합산한다")
    fun recommendSumsDuplicateRelatedRankScores() {
        // given: RPG 기록 게임 2개가 동일한 후보 게임을 1순위로 추천
        val user = saveUser("duplicate")
        val rpg = saveGenre(2001L, "RPG")

        val firstSource = saveGame(2001L, "첫 번째 기록", "60.0")
        val secondSource = saveGame(2002L, "두 번째 기록", "60.0")
        val candidate = saveGame(2003L, "공통 후보", "80.0")

        connectGenre(firstSource, rpg)
        connectGenre(secondSource, rpg)
        connectGenre(candidate, rpg)

        val firstRecord = saveUserGame(user, firstSource, PlayStatus.COMPLETED, playing = false)
        saveReview(firstRecord, "3.5")

        saveUserGame(user, secondSource, PlayStatus.PLAYED, playing = false)

        entityManager.flush()

        // when: 사용자 맞춤 추천을 조회
        val result = recommendationService.recommend(requireNotNull(user.id))

        // then: 동일 후보의 1순위 점수 20점씩과 장르·IGDB 점수가 합산
        assertThat(result.map { it.id })
            .containsExactly(requireNotNull(candidate.id))

        assertThat(result.first().recommendationScore)
            .isEqualByComparingTo("90.0")

        assertThat(result.first().genres.map { it.name })
            .containsExactly("RPG")
    }

    @Test
    @DisplayName("사용 가능한 게임 기록이 있으면 온보딩보다 기록 추천을 우선한다")
    fun recommendPrioritizesGameRecordsOverOnboarding() {
        // given: 게임 기록과 온보딩 선호 게임이 모두 존재
        val user = saveUser("priority")
        val rpg = saveGenre(2101L, "RPG")
        val action = saveGenre(2102L, "액션")

        val recordSource = saveGame(2101L, "기록 게임", "60.0")
        val recordCandidate = saveGame(2102L, "기록 추천 후보", "80.0")
        val preferredGame = saveGame(2103L, "온보딩 선호 게임", "60.0")
        val onboardingCandidate = saveGame(2104L, "온보딩 추천 후보", "90.0")

        connectGenre(recordSource, rpg)
        connectGenre(recordCandidate, rpg)
        connectGenre(preferredGame, action)
        connectGenre(onboardingCandidate, action)

        saveUserGame(user, recordSource, PlayStatus.COMPLETED, playing = false)

        entityManager.persist(UserPreferenceGame(user, preferredGame))
        entityManager.flush()

        // when: 사용자 맞춤 추천을 조회
        val result = recommendationService.recommend(requireNotNull(user.id))

        // then: 온보딩 후보를 사용하지 않고 기록 기반 후보만 반환
        assertThat(result.map { it.id })
            .containsExactly(requireNotNull(recordCandidate.id))
    }

    @Test
    @DisplayName("연관 추천 출발점이 없으면 온보딩 추천으로 전환한다")
    fun recommendFallsBackToOnboardingWhenNoRecordSourceExists() {
        // given: 기록 게임은 별점 3점이고 온보딩 선호 게임은 별도로 존재
        val user = saveUser("fallback")
        val rpg = saveGenre(2201L, "RPG")
        val action = saveGenre(2202L, "액션")

        val lowRatedRecord = saveGame(2201L, "낮은 별점 기록", "60.0")
        val preferredGame = saveGame(2202L, "온보딩 선호 게임", "60.0")
        val onboardingCandidate = saveGame(2203L, "온보딩 추천 후보", "80.0")

        connectGenre(lowRatedRecord, rpg)
        connectGenre(preferredGame, action)
        connectGenre(onboardingCandidate, action)

        val record = saveUserGame(user, lowRatedRecord, PlayStatus.COMPLETED, playing = false)
        saveReview(record, "3.0")

        entityManager.persist(UserPreferenceGame(user, preferredGame))
        entityManager.flush()

        // when: 사용자 맞춤 추천을 조회
        val result = recommendationService.recommend(requireNotNull(user.id))

        // then: 낮은 별점 기록 대신 온보딩 선호 게임의 연관 후보를 반환
        assertThat(result.map { it.id })
            .containsExactly(requireNotNull(onboardingCandidate.id))
    }

    @Test
    @DisplayName("선호 장르만 있으면 장르 점수와 IGDB 점수로 추천한다")
    fun recommendUsesGenresWhenPreferredGamesAreEmpty() {
        // given: 선호 게임 없이 RPG와 액션 장르만 선택
        val user = saveUser("genre-only")
        val rpg = saveGenre(2301L, "RPG")
        val action = saveGenre(2302L, "액션")

        val bothGenres = saveGame(2301L, "두 장르 후보", "80.0")
        val oneGenre = saveGame(2302L, "한 장르 후보", "90.0")
        val lowIgdbRating = saveGame(2303L, "IGDB 평점 미달", "69.0")

        connectGenre(bothGenres, rpg)
        connectGenre(bothGenres, action)
        connectGenre(oneGenre, rpg)
        connectGenre(lowIgdbRating, rpg)
        connectGenre(lowIgdbRating, action)

        entityManager.persist(UserPreferenceGenre(user, rpg))
        entityManager.persist(UserPreferenceGenre(user, action))
        entityManager.flush()

        // when: 사용자 맞춤 추천을 조회
        val result = recommendationService.recommend(requireNotNull(user.id))

        // then: 70점 이상 후보만 장르 가산점과 IGDB 점수 순서로 반환
        assertThat(result.map { it.id })
            .containsExactly(
                requireNotNull(bothGenres.id),
                requireNotNull(oneGenre.id)
            )

        assertThat(result[0].recommendationScore)
            .isEqualByComparingTo("60.0")

        assertThat(result[1].recommendationScore)
            .isEqualByComparingTo("55.0")

        assertThat(result[0].genres.map { it.name })
            .containsExactlyInAnyOrder("RPG", "액션")
    }

    @Test
    @DisplayName("이미 플레이 기록이 있는 게임은 다른 기록의 연관 후보여도 제외한다")
    fun recommendExcludesRecordedGamesFromRecordCandidates() {
        // given: 같은 장르의 기록 게임 2개와 아직 기록하지 않은 후보가 존재
        val user = saveUser("exclude-recorded")
        val action = saveGenre(2401L, "액션")

        val firstRecord = saveGame(2401L, "첫 번째 기록", "80.0")
        val secondRecord = saveGame(2402L, "두 번째 기록", "80.0")
        val newCandidate = saveGame(2403L, "새로운 후보", "70.0")

        connectGenre(firstRecord, action)
        connectGenre(secondRecord, action)
        connectGenre(newCandidate, action)

        val firstUserGame = saveUserGame(user, firstRecord, PlayStatus.COMPLETED, playing = false)
        saveReview(firstUserGame, "3.5")
        saveUserGame(user, secondRecord, PlayStatus.PLAYED, playing = false)
        entityManager.flush()

        // when: 사용자 맞춤 추천을 조회
        val result = recommendationService.recommend(requireNotNull(user.id))

        // then: 기록 게임들은 제외하고 아직 기록하지 않은 후보만 반환
        assertThat(result.map { it.id })
            .containsExactly(requireNotNull(newCandidate.id))
    }

    @Test
    @DisplayName("DROPPED 기록 게임은 온보딩 장르 후보에서도 제외한다")
    fun recommendExcludesDroppedGamesFromOnboardingCandidates() {
        // given: 선호 장르에 중단한 게임과 아직 기록하지 않은 게임이 존재
        val user = saveUser("exclude-dropped")
        val rpg = saveGenre(2501L, "RPG")

        val droppedGame = saveGame(2501L, "중단한 게임", "90.0")
        val newCandidate = saveGame(2502L, "새로운 후보", "80.0")

        connectGenre(droppedGame, rpg)
        connectGenre(newCandidate, rpg)

        saveUserGame(user, droppedGame, PlayStatus.DROPPED, playing = false)
        entityManager.persist(UserPreferenceGenre(user, rpg))
        entityManager.flush()

        // when: 사용자 맞춤 추천을 조회
        val result = recommendationService.recommend(requireNotNull(user.id))

        // then: 중단한 게임을 제외하고 새로운 후보만 반환
        assertThat(result.map { it.id })
            .containsExactly(requireNotNull(newCandidate.id))
    }

    @Test
    @DisplayName("플레이 상태 없이 별점만 남긴 게임도 온보딩 후보에서 제외한다")
    fun recommendExcludesRatedGamesFromOnboardingCandidates() {
        // given: 선호 장르에 별점만 남긴 게임과 아직 기록하지 않은 게임이 존재
        val user = saveUser("exclude-rated")
        val simulation = saveGenre(2601L, "시뮬레이션")

        val ratedGame = saveGame(2601L, "별점만 남긴 게임", "90.0")
        val newCandidate = saveGame(2602L, "새로운 후보", "80.0")

        connectGenre(ratedGame, simulation)
        connectGenre(newCandidate, simulation)

        val ratedUserGame = saveUserGame(user, ratedGame, null, playing = false)
        saveReview(ratedUserGame, "5.0")
        entityManager.persist(UserPreferenceGenre(user, simulation))
        entityManager.flush()

        // when: 사용자 맞춤 추천을 조회
        val result = recommendationService.recommend(requireNotNull(user.id))

        // then: 별점을 남긴 게임을 제외하고 새로운 후보만 반환
        assertThat(result.map { it.id })
            .containsExactly(requireNotNull(newCandidate.id))
    }

    @Test
    @DisplayName("게임 기록과 온보딩 선택이 모두 없으면 빈 목록을 반환한다")
    fun recommendReturnsEmptyListWhenUserHasNoData() {
        // given: 게임 기록과 온보딩 선택이 없는 사용자
        val user = saveUser("empty")
        entityManager.flush()

        // when: 사용자 맞춤 추천을 조회
        val result = recommendationService.recommend(requireNotNull(user.id))

        // then: 추천할 근거가 없으므로 빈 목록을 반환
        assertThat(result).isEmpty()
    }

    // 테스트용 게임을 저장
    private fun saveGame(igdbId: Long, title: String, igdbRating: String): Game {
        val game = Game.createFromIgdb(IgdbGameResponse(
            id = igdbId, name = title, summary = null, cover = null, first_release_date = null,
            rating = BigDecimal(igdbRating), involved_companies = mutableListOf(),
            genres = mutableListOf(), platforms = mutableListOf(), collections = mutableListOf(),
        ))

        entityManager.persist(game)

        return game
    }

    // 테스트용 장르를 저장
    private fun saveGenre(igdbGenreId: Long, name: String): Genre {
        val genre = Genre.createFromIgdb(igdbGenreId, name)
        entityManager.persist(genre)

        return genre
    }

    // 게임과 장르의 연결 정보를 저장
    private fun connectGenre(game: Game, genre: Genre) {
        entityManager.persist(GameGenre(game, genre))
    }

    // 테스트용 사용자를 저장
    private fun saveUser(suffix: String): User {
        val user = User("personalized-$suffix", "personalized-$suffix@test.com", "password")

        entityManager.persist(user)

        return user
    }

    // 테스트용 게임 기록을 저장
    private fun saveUserGame(
        user: User, game: Game, playStatus: PlayStatus?, playing: Boolean,
    ): UserGame {
        val userGame = UserGame(user, game)

        if (playStatus != null) {
            userGame.changePlayStatus(playStatus)
        }

        userGame.changePlaying(playing)
        entityManager.persist(userGame)

        return userGame
    }

    // 게임 기록에 사용자 별점 리뷰를 저장
    private fun saveReview(userGame: UserGame, rating: String) {
        entityManager.persist(Review(
            userGame,
            BigDecimal(rating),
            null,
            false
        ))
    }
}
