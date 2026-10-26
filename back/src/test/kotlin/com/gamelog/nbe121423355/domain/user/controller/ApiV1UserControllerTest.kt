package com.gamelog.nbe121423355.domain.user.controller

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.Genre
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.GenreRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.servlet.http.Cookie
import java.math.BigDecimal
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.aMapWithSize
import org.hamcrest.Matchers.hasKey
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class ApiV1UserControllerTest
@Autowired
constructor(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val genreRepository: GenreRepository,
    private val gameRepository: GameRepository,
) {
    private fun saveUser(email: String, nickname: String, rawPassword: String): User {
        return userRepository.save(User(nickname, email, passwordEncoder.encode(rawPassword)))
    }

    private fun accessTokenFor(user: User): String {
        return jwtProvider.generateAccessToken(user.id, user.role)
    }

    private fun saveGenre(name: String): Genre {
        return genreRepository.save(Genre.createFromIgdb(null, name))
    }

    private fun saveGame(title: String): Game {
        val response = IgdbGameResponse(
            title.hashCode().toLong(),
            title,
            "테스트 게임 설명",
            null,
            1704067200L,
            BigDecimal("90.50"),
            mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(),
        )
        return gameRepository.save(Game.createFromIgdb(response))
    }

    @Test
    @DisplayName("회원가입 성공")
    fun signUp_success() {
        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "테스트유저", "email": "test@test.com", "password": "password123"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resultCode").value("201-1"))
            .andExpect(jsonPath("$.data.email").value("test@test.com"))
            .andExpect(jsonPath("$.data.nickname").value("테스트유저"))
    }

    @Test
    @DisplayName("회원가입 실패 - 유효성 검증 (빈 닉네임)")
    fun signUp_fail_validation() {
        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "", "email": "test@test.com", "password": "password123"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("회원가입 실패 - 비밀번호 72바이트 초과 (한글 30자 = 90바이트)")
    fun signUp_fail_passwordTooLongBytes() {
        val password = "가".repeat(30)
        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "테스트유저", "email": "test@test.com", "password": "$password"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 72바이트 초과 시 500이 아닌 400")
    fun login_fail_passwordTooLongBytes() {
        val password = "a".repeat(73)
        mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@test.com", "password": "$password"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    fun signUp_fail_duplicateEmail() {
        saveUser("test@test.com", "other", "password123")

        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "테스트유저", "email": "test@test.com", "password": "password123"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-1"))
    }

    @Test
    @DisplayName("회원가입 실패 - 닉네임 중복")
    fun signUp_fail_duplicateNickname() {
        saveUser("other@test.com", "테스트유저", "password123")

        mockMvc.perform(
            post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "테스트유저", "email": "test@test.com", "password": "password123"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-2"))
    }

    @Test
    @DisplayName("로그인 성공 - accessToken 응답 + refreshToken 쿠키")
    fun login_success() {
        saveUser("test@test.com", "nickname", "password123")

        val result = mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@test.com", "password": "password123"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andReturn()

        val refreshToken = result.response.getCookie("refreshToken")
        assertThat(refreshToken).isNotNull
        assertThat(refreshToken!!.isHttpOnly).isTrue()
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    fun login_fail_wrongPassword() {
        saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@test.com", "password": "wrongPassword"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-1"))
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    fun refresh_success() {
        saveUser("test@test.com", "nickname", "password123")
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        val result = mockMvc.perform(post("/api/v1/users/refresh").cookie(refreshTokenCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-2"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andReturn()

        // Rotation: 교체된 refreshToken이 HttpOnly 쿠키로 다시 내려옴
        val rotatedCookie = result.response.getCookie("refreshToken")
        assertThat(rotatedCookie).isNotNull
        assertThat(rotatedCookie!!.isHttpOnly).isTrue()
        assertThat(rotatedCookie.maxAge).isPositive()
        assertThat(rotatedCookie.value).isNotEqualTo(refreshTokenCookie.value)
    }

    @Test
    @DisplayName("회원탈퇴 성공 - refreshToken 쿠키가 즉시 만료되고, 이후 그 쿠키로 재발급하면 401")
    fun withdraw_success_clearsCookie() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        val result = mockMvc.perform(
            delete("/api/v1/users/me")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password": "password123"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-19"))
            .andReturn()

        val clearedCookie = result.response.getCookie("refreshToken")
        assertThat(clearedCookie).isNotNull
        assertThat(clearedCookie!!.maxAge).isZero()
        mockMvc.perform(post("/api/v1/users/refresh").cookie(refreshTokenCookie))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("회원탈퇴 실패 - 비밀번호를 입력하지 않으면 400")
    fun withdraw_fail_blankPassword() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            delete("/api/v1/users/me")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password": ""}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("회원탈퇴 실패 - 로그인하지 않으면 401")
    fun withdraw_fail_unauthenticated() {
        mockMvc.perform(
            delete("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password": "password123"}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("비밀번호 변경 성공 - 현재 기기용 새 refreshToken 쿠키가 내려오고, 그 쿠키로 재발급 가능")
    fun changePassword_success_setsNewRefreshTokenCookie() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val oldCookie = loginAndGetRefreshTokenCookie()

        val result = mockMvc.perform(
            patch("/api/v1/users/me/password")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "password123", "newPassword": "newPassword123"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-14"))
            .andReturn()

        val newCookie = result.response.getCookie("refreshToken")
        assertThat(newCookie).isNotNull
        assertThat(newCookie!!.maxAge).isPositive()

        mockMvc.perform(post("/api/v1/users/refresh").cookie(newCookie))
            .andExpect(status().isOk)
        mockMvc.perform(post("/api/v1/users/refresh").cookie(oldCookie))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("토큰 재발급 실패 - RefreshToken DB에 없는 토큰(로그인을 거치지 않고 직접 생성한 토큰)")
    fun refresh_fail_tokenNotInDb() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val refreshToken = jwtProvider.generateRefreshToken(
            user.id, UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), jwtProvider.newRefreshTokenExpiration(),
        )

        mockMvc.perform(post("/api/v1/users/refresh").cookie(Cookie("refreshToken", refreshToken)))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-2"))
    }

    @Test
    @DisplayName("토큰 재발급 실패 - refreshToken 쿠키 없음(비로그인 상태) 시 500이 아닌 401")
    fun refresh_fail_noCookie() {
        mockMvc.perform(post("/api/v1/users/refresh"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-2"))
    }

    @Test
    @DisplayName("로그아웃 성공 - refreshToken 쿠키가 즉시 만료되고, 이후 그 토큰으로 재발급 시도하면 401")
    fun logout_success() {
        saveUser("test@test.com", "nickname", "password123")
        val refreshTokenCookie = loginAndGetRefreshTokenCookie()

        val logoutResult = mockMvc.perform(post("/api/v1/users/logout").cookie(refreshTokenCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-13"))
            .andReturn()

        val clearedCookie = logoutResult.response.getCookie("refreshToken")
        assertThat(clearedCookie).isNotNull
        assertThat(clearedCookie!!.maxAge).isZero()

        mockMvc.perform(post("/api/v1/users/refresh").cookie(refreshTokenCookie))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-2"))
    }

    @Test
    @DisplayName("로그아웃 - refreshToken 쿠키 없이 호출해도 200 (idempotent)")
    fun logout_withoutCookie_stillOk() {
        mockMvc.perform(post("/api/v1/users/logout"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-13"))
    }

    private fun loginAndGetRefreshTokenCookie(): Cookie {
        val loginResult = mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@test.com", "password": "password123"}"""),
        )
            .andExpect(status().isOk)
            .andReturn()
        val refreshTokenCookie = loginResult.response.getCookie("refreshToken")
        assertThat(refreshTokenCookie).isNotNull
        return refreshTokenCookie!!
    }

    @Test
    @DisplayName("내 정보 조회 - 인증 없이 호출하면 401")
    fun getMe_fail_unauthenticated() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.resultCode").value("401-1"))
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    fun getMe_success() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer ${accessTokenFor(user)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-3"))
            .andExpect(jsonPath("$.data.email").value("test@test.com"))
    }

    @Test
    @DisplayName("비로그인 사용자도 다른 사용자의 공개 정보를 조회할 수 있다")
    fun getPublicUserAllowsAnonymousUser() {
        // given: 공개 프로필로 조회할 사용자를 저장
        val profileUser = saveUser("profile@test.com", "프로필 사용자", "password123")

        // when: 인증 정보 없이 공개 사용자 정보를 조회
        mockMvc.perform(get("/api/v1/users/{userId}", profileUser.id))
            // then: 공개 정보만 반환하고 이메일은 노출하지 않는지 확인
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-3"))
            .andExpect(jsonPath("$.data.id").value(profileUser.id))
            .andExpect(jsonPath("$.data.nickname").value("프로필 사용자"))
            .andExpect(jsonPath("$.data.email").doesNotExist())
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 공개 정보는 조회할 수 없다")
    fun getPublicUserRejectsMissingUser() {
        // given: 존재하지 않는 사용자 ID를 준비
        val missingUserId = 999_999L

        // when: 없는 사용자의 공개 정보를 조회
        mockMvc.perform(get("/api/v1/users/{userId}", missingUserId))
            // then: 사용자 없음 응답을 반환하는지 확인
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    @Test
    @DisplayName("온보딩 완료 성공")
    fun onboarding_success() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(patch("/api/v1/users/me/onboarding").header("Authorization", "Bearer ${accessTokenFor(user)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-4"))

        assertThat(userRepository.findById(checkNotNull(user.id)).orElseThrow().onboardingCompleted).isTrue()
    }

    @Test
    @DisplayName("온보딩 스킵 성공")
    fun onboardingSkip_success() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(patch("/api/v1/users/me/onboarding/skip").header("Authorization", "Bearer ${accessTokenFor(user)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-5"))

        assertThat(userRepository.findById(checkNotNull(user.id)).orElseThrow().onboardingCompleted).isTrue()
    }

    @Test
    @DisplayName("선호 장르 저장 성공")
    fun updatePreferredGenres_success() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val action = saveGenre("액션")
        val rpg = saveGenre("RPG")

        mockMvc.perform(
            put("/api/v1/users/me/preferred-genres")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"genreIds": [${action.id}, ${rpg.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-6"))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    @DisplayName("선호 장르 저장 실패 - 최대 3개 초과")
    fun updatePreferredGenres_fail_tooMany() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val g1 = saveGenre("액션").id
        val g2 = saveGenre("RPG").id
        val g3 = saveGenre("어드벤처").id
        val g4 = saveGenre("퍼즐").id

        mockMvc.perform(
            put("/api/v1/users/me/preferred-genres")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"genreIds": [$g1, $g2, $g3, $g4]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-12"))
    }

    @Test
    @DisplayName("선호 장르 저장 - 인증 없이 호출하면 401")
    fun updatePreferredGenres_fail_unauthenticated() {
        mockMvc.perform(
            put("/api/v1/users/me/preferred-genres")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"genreIds": [1]}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("선호 장르 조회 성공")
    fun getPreferredGenres_success() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val action = saveGenre("액션")

        mockMvc.perform(
            put("/api/v1/users/me/preferred-genres")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"genreIds": [${action.id}]}"""),
        )

        mockMvc.perform(get("/api/v1/users/me/preferred-genres").header("Authorization", "Bearer ${accessTokenFor(user)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-7"))
            .andExpect(jsonPath("$.data[0].genreName").value("액션"))
    }

    @Test
    @DisplayName("선호 게임 저장 성공")
    fun updatePreferredGames_success() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val witcher = saveGame("The Witcher 3")

        mockMvc.perform(
            put("/api/v1/users/me/preferred-games")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gameIds": [${witcher.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-8"))
            .andExpect(jsonPath("$.data[0].gameTitle").value("The Witcher 3"))
    }

    @Test
    @DisplayName("선호 게임 조회 성공")
    fun getPreferredGames_success() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val witcher = saveGame("The Witcher 3")

        mockMvc.perform(
            put("/api/v1/users/me/preferred-games")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gameIds": [${witcher.id}]}"""),
        )

        mockMvc.perform(get("/api/v1/users/me/preferred-games").header("Authorization", "Bearer ${accessTokenFor(user)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-9"))
            .andExpect(jsonPath("$.data[0].gameTitle").value("The Witcher 3"))
    }

    @Test
    @DisplayName("이메일 중복 확인 - 존재하는 이메일이면 true")
    fun checkEmailDuplicate_true() {
        saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(get("/api/v1/users/check-email").param("email", "test@test.com"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").value(true))
    }

    @Test
    @DisplayName("이메일 중복 확인 - 존재하지 않는 이메일이면 false")
    fun checkEmailDuplicate_false() {
        mockMvc.perform(get("/api/v1/users/check-email").param("email", "unknown@test.com"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").value(false))
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 존재하는 닉네임이면 true")
    fun checkNicknameDuplicate_true() {
        saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(get("/api/v1/users/check-nickname").param("nickname", "nickname"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").value(true))
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 존재하지 않는 닉네임이면 false")
    fun checkNicknameDuplicate_false() {
        mockMvc.perform(get("/api/v1/users/check-nickname").param("nickname", "unknown"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").value(false))
    }

    @Test
    @DisplayName("프로필 수정 성공")
    fun updateProfile_success() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "newNickname", "profileImageUrl": "http://image.com/a.png", "bio": "안녕하세요"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-10"))
            .andExpect(jsonPath("$.data.nickname").value("newNickname"))
    }

    @Test
    @DisplayName("프로필 수정 실패 - 닉네임 중복")
    fun updateProfile_fail_duplicateNickname() {
        saveUser("other@test.com", "takenNickname", "password123")
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "takenNickname", "profileImageUrl": null, "bio": null}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-2"))
    }

    @Test
    @DisplayName("프로필 수정 실패 - 인증 없이 호출하면 401")
    fun updateProfile_fail_unauthenticated() {
        mockMvc.perform(
            patch("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "newNickname", "profileImageUrl": null, "bio": null}"""),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("내 정보 응답 - 7개 키를 모두 내려주고 값이 없는 필드는 null로 유지")
    fun getMe_responseShape() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer ${accessTokenFor(user)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", aMapWithSize<String, Any>(7)))
            .andExpect(jsonPath("$.data.id").value(user.id))
            .andExpect(jsonPath("$.data.nickname").value("nickname"))
            .andExpect(jsonPath("$.data.email").value("test@test.com"))
            .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
            .andExpect(jsonPath("$.data.role").value("USER"))
            .andExpect(jsonPath("$.data", hasKey("profileImageUrl")))
            .andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data", hasKey("bio")))
            .andExpect(jsonPath("$.data.bio").value(nullValue()))
    }

    @Test
    @DisplayName("공개 정보 응답 - 4개 키만 내려주고 값이 없는 필드는 null로 유지")
    fun getPublicUser_responseShape() {
        val user = saveUser("profile@test.com", "프로필 사용자", "password123")

        mockMvc.perform(get("/api/v1/users/{userId}", user.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", aMapWithSize<String, Any>(4)))
            .andExpect(jsonPath("$.data.nickname").value("프로필 사용자"))
            .andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data", hasKey("bio")))
            .andExpect(jsonPath("$.data.bio").value(nullValue()))
    }

    @Test
    @DisplayName("로그인 응답 - user와 accessToken을 함께 내려준다")
    fun login_responseShape() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@test.com", "password": "password123"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", aMapWithSize<String, Any>(2)))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.user", aMapWithSize<String, Any>(7)))
            .andExpect(jsonPath("$.data.user.id").value(user.id))
            .andExpect(jsonPath("$.data.user.nickname").value("nickname"))
            .andExpect(jsonPath("$.data.user.role").value("USER"))
    }

    @Test
    @DisplayName("프로필 수정 - 보내지 않은 profileImageUrl, bio는 null로 저장")
    fun updateProfile_omittedFieldsBecomeNull() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"nickname": "newNickname"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("newNickname"))
            .andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.bio").value(nullValue()))
    }

    @Test
    @DisplayName("로그인 실패 - password 필드가 없으면 400-1")
    fun login_fail_missingField() {
        mockMvc.perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "test@test.com"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }

    @Test
    @DisplayName("프로필 수정 실패 - nickname 필드가 없으면 400-1")
    fun updateProfile_fail_missingField() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            patch("/api/v1/users/me")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bio": "안녕하세요"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - newPassword 필드가 없으면 400-1")
    fun changePassword_fail_missingField() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            patch("/api/v1/users/me/password")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "password123"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }

    @Test
    @DisplayName("회원탈퇴 실패 - password 필드가 없으면 400-1")
    fun withdraw_fail_missingField() {
        val user = saveUser("test@test.com", "nickname", "password123")

        mockMvc.perform(
            delete("/api/v1/users/me")
                .header("Authorization", "Bearer ${accessTokenFor(user)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-1"))
    }
}
