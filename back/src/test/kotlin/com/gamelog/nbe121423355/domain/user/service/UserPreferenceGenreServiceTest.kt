package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.repository.GenreRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserPreferenceGenreRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
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
class UserPreferenceGenreServiceTest
@Autowired
constructor(
    private val userPreferenceGenreService: UserPreferenceGenreService,
    private val userPreferenceGenreRepository: UserPreferenceGenreRepository,
    private val userRepository: UserRepository,
    private val genreRepository: GenreRepository,
) {
    private val User.requiredId: Long get() = checkNotNull(id)
    private val Genre.requiredId: Long get() = checkNotNull(id)

    private fun saveUser(): User {
        return userRepository.save(User("테스트", "test@test.com", "password"))
    }

    private fun saveGenre(name: String): Genre {
        return genreRepository.save(Genre.createFromIgdb(null, name))
    }

    @Test
    @DisplayName("선호 장르 저장 성공")
    fun updatePreferredGenres_success() {
        val user = saveUser()
        val action = saveGenre("액션")
        val rpg = saveGenre("RPG")

        val result = userPreferenceGenreService.updatePreferredGenres(user.requiredId, listOf(action.requiredId, rpg.requiredId))

        assertThat(result).hasSize(2)
        assertThat(result.map { it.genreName })
            .containsExactlyInAnyOrder("액션", "RPG")
        assertThat(userPreferenceGenreRepository.findByUser_Id(user.id)).hasSize(2)
    }

    @Test
    @DisplayName("선호 장르 재저장 시 기존 선택이 교체된다")
    fun updatePreferredGenres_replacesExisting() {
        val user = saveUser()
        val action = saveGenre("액션")
        val rpg = saveGenre("RPG")
        userPreferenceGenreService.updatePreferredGenres(user.requiredId, listOf(action.requiredId))

        val result = userPreferenceGenreService.updatePreferredGenres(user.requiredId, listOf(rpg.requiredId))

        assertThat(result.map { it.genreName }).containsExactly("RPG")
        assertThat(userPreferenceGenreRepository.findByUser_Id(user.id)).hasSize(1)
    }

    @Test
    @DisplayName("선호 장르 저장 실패 - 최대 3개 초과")
    fun updatePreferredGenres_fail_tooMany() {
        val user = saveUser()
        val g1 = saveGenre("액션")
        val g2 = saveGenre("RPG")
        val g3 = saveGenre("어드벤처")
        val g4 = saveGenre("퍼즐")

        assertThatThrownBy {
            userPreferenceGenreService.updatePreferredGenres(
                user.requiredId, listOf(g1.requiredId, g2.requiredId, g3.requiredId, g4.requiredId),
            )
        }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("400-12") })
    }

    @Test
    @DisplayName("선호 장르 저장 실패 - 존재하지 않는 유저")
    fun updatePreferredGenres_fail_userNotFound() {
        val genre = saveGenre("액션")

        assertThatThrownBy { userPreferenceGenreService.updatePreferredGenres(999_999L, listOf(genre.requiredId)) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("404-1") })
    }

    @Test
    @DisplayName("선호 장르 저장 실패 - 존재하지 않는 장르 포함")
    fun updatePreferredGenres_fail_nonexistentGenre() {
        val user = saveUser()
        val action = saveGenre("액션")

        assertThatThrownBy {
            userPreferenceGenreService.updatePreferredGenres(user.requiredId, listOf(action.requiredId, 999_999L))
        }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("400-11") })
    }

    @Test
    @DisplayName("선호 장르 조회 성공")
    fun getPreferredGenres_success() {
        val user = saveUser()
        val action = saveGenre("액션")
        userPreferenceGenreService.updatePreferredGenres(user.requiredId, listOf(action.requiredId))

        val result = userPreferenceGenreService.getPreferredGenres(user.id)

        assertThat(result).hasSize(1)
        assertThat(result[0].genreId).isEqualTo(action.id)
        assertThat(result[0].genreName).isEqualTo("액션")
    }

    @Test
    @DisplayName("선호 장르 조회 - 선택한 적 없으면 빈 리스트")
    fun getPreferredGenres_empty() {
        val user = saveUser()

        val result = userPreferenceGenreService.getPreferredGenres(user.id)

        assertThat(result).isEmpty()
    }
}
