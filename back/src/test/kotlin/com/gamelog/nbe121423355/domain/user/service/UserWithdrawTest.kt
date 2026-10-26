package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.GenreRepository
import com.gamelog.nbe121423355.domain.review.entity.Review
import com.gamelog.nbe121423355.domain.review.entity.ReviewStatus
import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.user.dto.LoginRequestDto
import com.gamelog.nbe121423355.domain.user.dto.SignupRequestDto
import com.gamelog.nbe121423355.domain.user.dto.UpdateProfileRequestDto
import com.gamelog.nbe121423355.domain.user.dto.WithdrawRequestDto
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFavoriteGame
import com.gamelog.nbe121423355.domain.user.entity.UserFollow
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGame
import com.gamelog.nbe121423355.domain.user.entity.UserPreferenceGenre
import com.gamelog.nbe121423355.domain.user.repository.RefreshTokenRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserPreferenceGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserPreferenceGenreRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameSearchRequest
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import com.gamelog.nbe121423355.domain.usergame.repository.UserGameRepository
import com.gamelog.nbe121423355.domain.usergame.service.UserGameService
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.upload.ImageUploadService
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class UserWithdrawTest
@Autowired
constructor(
    private val userService: UserService,
    private val userFollowService: UserFollowService,
    private val userGameService: UserGameService,
    private val tokenReissueService: TokenReissueService,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userFollowRepository: UserFollowRepository,
    private val userPreferenceGenreRepository: UserPreferenceGenreRepository,
    private val userPreferenceGameRepository: UserPreferenceGameRepository,
    private val userFavoriteGameRepository: UserFavoriteGameRepository,
    private val gameRepository: GameRepository,
    private val genreRepository: GenreRepository,
    private val userGameRepository: UserGameRepository,
    private val reviewRepository: ReviewRepository,
    private val passwordEncoder: PasswordEncoder,
    private val entityManager: EntityManager,
) {
    @MockitoBean
    private lateinit var imageUploadService: ImageUploadService

    private val User.requiredId: Long get() = checkNotNull(id)
    private val Game.requiredId: Long get() = checkNotNull(id)

    private fun saveUser(email: String, nickname: String): User {
        return userRepository.save(User(nickname, email, passwordEncoder.encode(PASSWORD)))
    }

    private fun saveGame(igdbId: Long, title: String): Game {
        return gameRepository.save(
            Game.createFromIgdb(
                IgdbGameResponse(
                    igdbId, title, "test", null, null, null,
                    mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
                ),
            ),
        )
    }

    private fun saveReview(user: User, game: Game): Review {
        val userGame = userGameRepository.save(UserGame(user, game))
        return reviewRepository.save(Review(userGame, BigDecimal("4.5"), "리뷰 내용", false))
    }

    private fun withdraw(user: User) {
        userService.withdraw(user.id, WithdrawRequestDto(PASSWORD))
    }

    // 벌크 쿼리 결과를 확인하려면 영속성 컨텍스트를 비우고 DB에서 다시 읽어야 함
    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    private fun assertResultCode(e: Throwable, resultCode: String) {
        assertThat(e).isInstanceOf(ServiceException::class.java)
        assertThat((e as ServiceException).resultCode).isEqualTo(resultCode)
    }

    @Test
    @DisplayName("탈퇴 성공 - 개인정보가 익명화되고 deletedAt이 기록됨 (DB에서 다시 읽어 확인)")
    fun withdraw_anonymizesUser() {
        val user = saveUser("withdraw@test.com", "withdrawUser")
        userService.updateProfile(
            user.requiredId,
            UpdateProfileRequestDto("withdrawUser", "https://res.cloudinary.com/demo/image/upload/v1/profile.png", "소개"),
        )

        withdraw(user)
        flushAndClear()

        val withdrawn = userRepository.findById(user.requiredId).orElseThrow()
        assertThat(withdrawn.email).isEqualTo("deleted_${user.id}@deleted.local")
        assertThat(withdrawn.nickname).isEqualTo("탈퇴한사용자_${user.id}")
        assertThat(withdrawn.profileImageUrl).isNull()
        assertThat(withdrawn.bio).isNull()
        assertThat(withdrawn.isDeleted).isTrue()
        assertThat(passwordEncoder.matches(PASSWORD, withdrawn.password)).isFalse()
        // 테스트 트랜잭션은 커밋되지 않으므로 커밋 후 이미지 삭제도 실행되지 않음
        verify(imageUploadService, never()).deleteImage(anyString())
    }

    @Test
    @DisplayName("탈퇴 성공 - 세션, 팔로우 양방향, 선호 장르/게임, 인생 게임이 삭제됨")
    fun withdraw_deletesUserDomainData() {
        val user = saveUser("withdraw@test.com", "withdrawUser")
        val other = saveUser("other@test.com", "otherUser")
        val game = saveGame(700001L, "withdraw-game")
        val genre = genreRepository.save(Genre.createFromIgdb(null, "withdraw-genre"))
        userService.login(LoginRequestDto("withdraw@test.com", PASSWORD))
        userService.login(LoginRequestDto("withdraw@test.com", PASSWORD))
        userFollowRepository.save(UserFollow(user, other))
        userFollowRepository.save(UserFollow(other, user))
        userPreferenceGenreRepository.save(UserPreferenceGenre(user, genre))
        userPreferenceGameRepository.save(UserPreferenceGame(user, game))
        userFavoriteGameRepository.save(UserFavoriteGame(user, game, 1))

        withdraw(user)
        flushAndClear()

        assertThat(refreshTokenRepository.findAll()).noneMatch { token -> token.user.id == user.id }
        assertThat(userFollowRepository.existsByFollowerIdAndFolloweeId(user.id, other.id)).isFalse()
        assertThat(userFollowRepository.existsByFollowerIdAndFolloweeId(other.id, user.id)).isFalse()
        assertThat(userPreferenceGenreRepository.findByUser_Id(user.id)).isEmpty()
        assertThat(userPreferenceGameRepository.findByUser_Id(user.id)).isEmpty()
        assertThat(userFavoriteGameRepository.findAllByUserIdOrderByDisplayOrderAsc(user.id)).isEmpty()
    }

    @Test
    @DisplayName("탈퇴 성공 - 활성 리뷰만 숨김, 관리자가 숨긴 리뷰와 다른 유저 리뷰는 그대로, 라이브러리 row는 유지")
    fun withdraw_hidesOnlyActiveReviews() {
        val user = saveUser("withdraw@test.com", "withdrawUser")
        val other = saveUser("other@test.com", "otherUser")
        val game1 = saveGame(700001L, "withdraw-game-1")
        val game2 = saveGame(700002L, "withdraw-game-2")
        val activeReview = saveReview(user, game1)
        val adminHiddenReview = saveReview(user, game2)
        adminHiddenReview.hideByAdmin()
        val otherReview = saveReview(other, game1)

        withdraw(user)
        flushAndClear()

        assertThat(reviewRepository.findById(checkNotNull(activeReview.id)).orElseThrow().status).isEqualTo(ReviewStatus.DELETED_BY_USER)
        assertThat(reviewRepository.findById(checkNotNull(activeReview.id)).orElseThrow().deletedAt).isNotNull()
        assertThat(reviewRepository.findById(checkNotNull(adminHiddenReview.id)).orElseThrow().status).isEqualTo(ReviewStatus.HIDDEN_BY_ADMIN)
        assertThat(reviewRepository.findById(checkNotNull(otherReview.id)).orElseThrow().isActive).isTrue()
        assertThat(userGameRepository.findByUser_IdAndGame_Id(user.requiredId, game1.requiredId)).isNotNull()
    }

    @Test
    @DisplayName("탈퇴 실패 - 비밀번호가 틀리면 400-13, 아무것도 바뀌지 않음")
    fun withdraw_fail_wrongPassword() {
        val user = saveUser("withdraw@test.com", "withdrawUser")

        assertThatThrownBy { userService.withdraw(user.id, WithdrawRequestDto("wrongPassword")) }
            .satisfies({ e -> assertResultCode(e, "400-13") })
        assertThat(userRepository.findById(user.requiredId).orElseThrow().isDeleted).isFalse()
    }

    @Test
    @DisplayName("탈퇴 실패 - 관리자 계정은 403-2")
    fun withdraw_fail_admin() {
        val admin = saveUser("admin@test.com", "adminUser")
        admin.promoteToAdmin()

        assertThatThrownBy { withdraw(admin) }
            .satisfies({ e -> assertResultCode(e, "403-2") })
    }

    @Test
    @DisplayName("탈퇴 실패 - 이미 탈퇴한 유저는 404-1")
    fun withdraw_fail_alreadyWithdrawn() {
        val user = saveUser("withdraw@test.com", "withdrawUser")
        withdraw(user)

        assertThatThrownBy { withdraw(user) }
            .satisfies({ e -> assertResultCode(e, "404-1") })
    }

    @Test
    @DisplayName("탈퇴 후 - 예전 이메일로 로그인 실패, 같은 이메일·닉네임으로 재가입 가능")
    fun afterWithdraw_loginFails_and_rejoinAllowed() {
        val user = saveUser("withdraw@test.com", "withdrawUser")
        withdraw(user)
        flushAndClear()

        assertThatThrownBy { userService.login(LoginRequestDto("withdraw@test.com", PASSWORD)) }
            .satisfies({ e -> assertResultCode(e, "401-1") })

        val newUserId = userService.signUp(SignupRequestDto("withdrawUser", "withdraw@test.com", PASSWORD)).id
        assertThat(newUserId).isNotEqualTo(user.id)
    }

    @Test
    @DisplayName("탈퇴 후 - 탈퇴한 유저의 refreshToken으로 재발급 불가")
    fun afterWithdraw_reissueFails() {
        val user = saveUser("withdraw@test.com", "withdrawUser")
        val refreshToken = userService.login(LoginRequestDto("withdraw@test.com", PASSWORD)).refreshToken
        withdraw(user)
        flushAndClear()

        assertThatThrownBy { tokenReissueService.reissue(refreshToken) }
            .satisfies({ e -> assertResultCode(e, "401-2") })
    }

    @Test
    @DisplayName("탈퇴 후 - 공개 프로필, 권한 변경, 팔로우, 팔로우 목록은 404, 검색에서는 제외")
    fun afterWithdraw_userDomainBlocked() {
        val user = saveUser("withdraw@test.com", "withdrawUser")
        val viewer = saveUser("viewer@test.com", "viewerUser")
        withdraw(user)
        flushAndClear()

        assertThatThrownBy { userService.getUser(user.id) }.satisfies({ e -> assertResultCode(e, "404-1") })
        assertThatThrownBy { userService.promoteToAdmin(user.id) }.satisfies({ e -> assertResultCode(e, "404-1") })
        assertThatThrownBy { userFollowService.follow(viewer.requiredId, user.id) }.satisfies({ e -> assertResultCode(e, "404-1") })
        assertThatThrownBy { userFollowService.getFollowers(viewer.id, user.id, 0, 10) }.satisfies({ e -> assertResultCode(e, "404-1") })
        assertThat(userFollowService.searchUsers(viewer.id, "탈퇴한사용자", 0, 10).users).isEmpty()
    }

    @Test
    @DisplayName("탈퇴 후 - 탈퇴한 유저의 공개 라이브러리와 프로필 조회는 404")
    fun afterWithdraw_libraryBlocked() {
        val user = saveUser("withdraw@test.com", "withdrawUser")
        val viewer = saveUser("viewer@test.com", "viewerUser")
        withdraw(user)
        flushAndClear()

        assertThatThrownBy { userGameService.getPublicUserGameList(user.requiredId, UserGameSearchRequest()) }
            .satisfies({ e -> assertResultCode(e, "404-1") })
        assertThatThrownBy { userGameService.profileTab(user.requiredId, viewer.id) }
            .satisfies({ e -> assertResultCode(e, "404-1") })
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("트랜잭션 적용 - 테스트 트랜잭션 없이 탈퇴해도 익명화·리뷰 숨김이 커밋되고, 커밋 후 프로필 이미지가 삭제됨")
    fun withdraw_withoutTestTransaction() {
        // 테스트 트랜잭션이 있으면 Propagation.MANDATORY, afterCommit, clearAutomatically 이후 변경 유실 같은
        // 실제 트랜잭션 경계 문제가 가려지므로 이 테스트만 트랜잭션 없이 실행 (데이터가 커밋되므로 직접 정리)
        val user = saveUser("no-tx-withdraw@test.com", "noTxWithdrawUser")
        val game = saveGame(700099L, "no-tx-withdraw-game")
        val review = saveReview(user, game)
        val imageUrl = "https://res.cloudinary.com/demo/image/upload/v1/no-tx.png"
        userService.updateProfile(user.requiredId, UpdateProfileRequestDto("noTxWithdrawUser", imageUrl, null))
        try {
            withdraw(user)

            val withdrawn = userRepository.findById(user.requiredId).orElseThrow()
            assertThat(withdrawn.isDeleted).isTrue()
            assertThat(withdrawn.email).isEqualTo("deleted_${user.id}@deleted.local")
            assertThat(reviewRepository.findById(checkNotNull(review.id)).orElseThrow().status).isEqualTo(ReviewStatus.DELETED_BY_USER)
            verify(imageUploadService).deleteImage(imageUrl)
        } finally {
            reviewRepository.deleteById(checkNotNull(review.id))
            userGameRepository.findByUser_IdAndGame_Id(user.requiredId, game.requiredId)?.let { userGameRepository.delete(it) }
            gameRepository.deleteById(game.requiredId)
            userRepository.deleteById(user.requiredId)
        }
    }

    companion object {
        private const val PASSWORD = "password123"
    }
}
