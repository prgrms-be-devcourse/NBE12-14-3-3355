package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.user.dto.ChangePasswordRequestDto
import com.gamelog.nbe121423355.domain.user.dto.LoginRequestDto
import com.gamelog.nbe121423355.domain.user.dto.SignupRequestDto
import com.gamelog.nbe121423355.domain.user.dto.UpdateProfileRequestDto
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.RefreshTokenRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.global.upload.ImageUploadService
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class UserServiceTest
@Autowired
constructor(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    @MockitoBean
    private lateinit var imageUploadService: ImageUploadService

    private val User.requiredId: Long get() = checkNotNull(id)

    private fun saveUser(email: String, nickname: String, rawPassword: String): User {
        return userRepository.save(User(nickname, email, passwordEncoder.encode(rawPassword)))
    }

    // refreshToken의 로그인 세션(family) ID
    private fun familyIdOf(refreshToken: String): String? {
        return jwtProvider.getFamilyId(jwtProvider.parseClaims(refreshToken))
    }

    @Test
    @DisplayName("회원가입 성공")
    fun signUp_success() {
        val signupRequestDto = SignupRequestDto("nickname", "test@test.com", "password123")

        val userDto = userService.signUp(signupRequestDto)

        assertThat(userDto.email).isEqualTo("test@test.com")
        assertThat(userDto.nickname).isEqualTo("nickname")
        val saved = userRepository.findByEmail("test@test.com").orElseThrow()
        assertThat(saved.id).isEqualTo(userDto.id)
        assertThat(passwordEncoder.matches("password123", saved.password)).isTrue()
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    fun signUp_fail_duplicateEmail() {
        saveUser("test@test.com", "other", "password123")
        val signupRequestDto = SignupRequestDto("nickname", "test@test.com", "password123")

        assertThatThrownBy { userService.signUp(signupRequestDto) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("409-1") })
    }

    @Test
    @DisplayName("회원가입 실패 - 닉네임 중복")
    fun signUp_fail_duplicateNickname() {
        saveUser("other@test.com", "nickname", "password123")
        val signupRequestDto = SignupRequestDto("nickname", "test@test.com", "password123")

        assertThatThrownBy { userService.signUp(signupRequestDto) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("409-2") })
    }

    @Test
    @DisplayName("로그인 성공")
    fun login_success() {
        saveUser("test@test.com", "nickname", "password123")
        val loginRequestDto = LoginRequestDto("test@test.com", "password123")

        val result = userService.login(loginRequestDto)

        assertThat(result.accessToken).isNotBlank()
        assertThat(result.refreshToken).isNotBlank()
        assertThat(result.user.email).isEqualTo("test@test.com")
    }

    @Test
    @DisplayName("로그인 성공 - accessToken 클레임에 userId/role만 있고 email/nickname은 없음")
    fun login_accessTokenClaims_excludeEmailAndNickname() {
        saveUser("test@test.com", "nickname", "password123")
        val loginRequestDto = LoginRequestDto("test@test.com", "password123")

        val result = userService.login(loginRequestDto)
        val claims = jwtProvider.parseClaims(result.accessToken)

        assertThat(claims["email"]).isNull()
        assertThat(claims["nickname"]).isNull()
        assertThat(claims.get("role", String::class.java)).isEqualTo("USER")
        assertThat(claims.subject.toLong()).isEqualTo(result.user.id)
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일")
    fun login_fail_emailNotFound() {
        val loginRequestDto = LoginRequestDto("unknown@test.com", "password123")

        assertThatThrownBy { userService.login(loginRequestDto) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-1") })
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    fun login_fail_wrongPassword() {
        saveUser("test@test.com", "nickname", "password123")
        val loginRequestDto = LoginRequestDto("test@test.com", "wrongPassword")

        assertThatThrownBy { userService.login(loginRequestDto) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-1") })
    }

    @Test
    @DisplayName("토큰 재발급 성공")
    fun refresh_success() {
        saveUser("test@test.com", "nickname", "password123")
        val loginRequestDto = LoginRequestDto("test@test.com", "password123")
        val loginResult = userService.login(loginRequestDto)

        val result = userService.refresh(loginResult.refreshToken)

        assertThat(result.accessToken).isNotBlank()
        assertThat(jwtProvider.getUserId(result.accessToken)).isEqualTo(loginResult.user.id)
    }

    @Test
    @DisplayName("토큰 재발급 실패 - refreshToken 없음(null)")
    fun refresh_fail_nullToken() {
        assertThatThrownBy { userService.refresh(null) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-2") })
    }

    @Test
    @DisplayName("토큰 재발급 실패 - 유효하지 않은 토큰")
    fun refresh_fail_invalidToken() {
        assertThatThrownBy { userService.refresh("invalid-token") }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-2") })
    }

    @Test
    @DisplayName("토큰 재발급 실패 - RefreshToken DB에 없는 토큰(로그아웃되었거나 직접 생성된 토큰)")
    fun refresh_fail_userNotFound() {
        val refreshToken = jwtProvider.generateRefreshToken(
            999_999L, UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), jwtProvider.newRefreshTokenExpiration(),
        )

        assertThatThrownBy { userService.refresh(refreshToken) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-2") })
    }

    @Test
    @DisplayName("로그아웃 성공 - RefreshToken row가 삭제되고, 이후 같은 토큰으로 재발급 시도하면 실패함")
    fun logout_success() {
        saveUser("test@test.com", "nickname", "password123")
        val loginRequestDto = LoginRequestDto("test@test.com", "password123")
        val loginResult = userService.login(loginRequestDto)
        val familyId = familyIdOf(loginResult.refreshToken)
        assertThat(refreshTokenRepository.findByFamilyId(familyId)).isPresent()

        userService.logout(loginResult.refreshToken)

        assertThat(refreshTokenRepository.findByFamilyId(familyId)).isEmpty()
        assertThatThrownBy { userService.refresh(loginResult.refreshToken) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-2") })
    }

    @Test
    @DisplayName("토큰 재발급 실패 - refreshToken 자리에 accessToken을 넣으면 401-2")
    fun refresh_fail_accessToken() {
        saveUser("test@test.com", "nickname", "password123")
        val loginResult = userService.login(LoginRequestDto("test@test.com", "password123"))

        assertThatThrownBy { userService.refresh(loginResult.accessToken) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-2") })
    }

    @Test
    @DisplayName("비밀번호 변경 성공 - 기존 모든 기기의 세션이 삭제되고, 기존 토큰으로 재발급 시도하면 실패함")
    fun changePassword_success_revokesAllRefreshTokens() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val loginRequestDto = LoginRequestDto("test@test.com", "password123")
        val device1 = userService.login(loginRequestDto)
        val device2 = userService.login(loginRequestDto)
        val familyId1 = familyIdOf(device1.refreshToken)
        val familyId2 = familyIdOf(device2.refreshToken)

        userService.changePassword(user.requiredId, ChangePasswordRequestDto("password123", "newPassword123"))

        assertThat(refreshTokenRepository.findByFamilyId(familyId1)).isEmpty()
        assertThat(refreshTokenRepository.findByFamilyId(familyId2)).isEmpty()
        assertThatThrownBy { userService.refresh(device1.refreshToken) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-2") })
    }

    @Test
    @DisplayName("비밀번호 변경 성공 - 현재 기기용 새 세션이 발급되어 그 refreshToken으로 재발급 가능")
    fun changePassword_success_issuesNewSessionForCurrentDevice() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val current = userService.login(LoginRequestDto("test@test.com", "password123"))

        val session = userService.changePassword(user.requiredId, ChangePasswordRequestDto("password123", "newPassword123"))

        val newFamilyId = familyIdOf(session.refreshToken)
        assertThat(newFamilyId).isNotEqualTo(familyIdOf(current.refreshToken))
        assertThat(refreshTokenRepository.findByFamilyId(newFamilyId)).isPresent()
        assertThat(userService.refresh(session.refreshToken).userId).isEqualTo(user.id)
    }

    @Test
    @DisplayName("비밀번호 변경 성공 - 새 비밀번호로 로그인되고 기존 비밀번호로는 실패함")
    fun changePassword_success_newPasswordApplied() {
        val user = saveUser("test@test.com", "nickname", "password123")
        userService.login(LoginRequestDto("test@test.com", "password123"))

        userService.changePassword(user.requiredId, ChangePasswordRequestDto("password123", "newPassword123"))

        assertDoesNotThrow { userService.login(LoginRequestDto("test@test.com", "newPassword123")) }
        assertThatThrownBy { userService.login(LoginRequestDto("test@test.com", "password123")) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-1") })
    }

    @Test
    @DisplayName("비밀번호 변경 - 다른 유저의 RefreshToken은 삭제되지 않음")
    fun changePassword_keepsOtherUsersRefreshTokens() {
        val user = saveUser("test@test.com", "nickname", "password123")
        saveUser("other@test.com", "other", "password123")
        val other = userService.login(LoginRequestDto("other@test.com", "password123"))
        val otherFamilyId = familyIdOf(other.refreshToken)

        userService.changePassword(user.requiredId, ChangePasswordRequestDto("password123", "newPassword123"))

        assertThat(refreshTokenRepository.findByFamilyId(otherFamilyId)).isPresent()
    }

    @Test
    @DisplayName("로그아웃 - 이미 무효하거나 형식이 잘못된 토큰이어도 예외 없이 통과")
    fun logout_invalidToken_noException() {
        assertDoesNotThrow { userService.logout("invalid-token") }
    }

    @Test
    @DisplayName("멀티 디바이스 - 같은 유저로 두 번 로그인하면 각각 별도 세션으로 관리되어, 한쪽 로그아웃이 다른 쪽에 영향 없음")
    fun login_twice_independentSessions() {
        saveUser("test@test.com", "nickname", "password123")
        val loginRequestDto = LoginRequestDto("test@test.com", "password123")

        val device1 = userService.login(loginRequestDto)
        val device2 = userService.login(loginRequestDto)
        userService.logout(device1.refreshToken)

        assertThatThrownBy { userService.refresh(device1.refreshToken) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("401-2") })
        assertThat(userService.refresh(device2.refreshToken).accessToken).isNotBlank()
    }

    @Test
    @DisplayName("유저 정보 호출 - 성공")
    fun getMe_success() {
        val user = saveUser("test@test.com", "nickname", "password123")

        val userDto = userService.getMe(user.requiredId)

        assertThat(userDto.id).isEqualTo(user.id)
        assertThat(userDto.email).isEqualTo("test@test.com")
        assertThat(userDto.nickname).isEqualTo("nickname")
    }

    @Test
    @DisplayName("유저 정보 호출 - 실패 (존재하지 않는 유저)")
    fun getMe_fail_userNotFound() {
        assertThatThrownBy { userService.getMe(999_999L) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("404-1") })
    }

    @Test
    @DisplayName("온보딩 완료 성공")
    fun exitOnboarding_success() {
        val user = saveUser("test@test.com", "nickname", "password123")

        val userDto = userService.exitOnboarding(user.requiredId)

        assertThat(userDto.id).isEqualTo(user.id)
        assertThat(userRepository.findById(user.requiredId).orElseThrow().onboardingCompleted).isTrue()
    }

    @Test
    @DisplayName("온보딩 완료 실패 - 존재하지 않는 유저")
    fun exitOnboarding_fail_userNotFound() {
        assertThatThrownBy { userService.exitOnboarding(999_999L) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("404-1") })
    }

    @Test
    @DisplayName("온보딩 스킵 성공")
    fun skipOnboarding_success() {
        val user = saveUser("test@test.com", "nickname", "password123")

        val userDto = userService.skipOnboarding(user.requiredId)

        assertThat(userDto.id).isEqualTo(user.id)
        assertThat(userRepository.findById(user.requiredId).orElseThrow().onboardingCompleted).isTrue()
    }

    @Test
    @DisplayName("온보딩 스킵 실패 - 존재하지 않는 유저")
    fun skipOnboarding_fail_userNotFound() {
        assertThatThrownBy { userService.skipOnboarding(999_999L) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("404-1") })
    }

    @Test
    @DisplayName("이메일 중복 확인 - 존재하는 이메일이면 true")
    fun checkEmailDuplicate_true() {
        saveUser("test@test.com", "nickname", "password123")

        assertThat(userService.checkEmailDuplicate("test@test.com")).isTrue()
    }

    @Test
    @DisplayName("이메일 중복 확인 - 존재하지 않는 이메일이면 false")
    fun checkEmailDuplicate_false() {
        assertThat(userService.checkEmailDuplicate("unknown@test.com")).isFalse()
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 존재하는 닉네임이면 true")
    fun checkNicknameDuplicate_true() {
        saveUser("test@test.com", "nickname", "password123")

        assertThat(userService.checkNicknameDuplicate("nickname")).isTrue()
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 존재하지 않는 닉네임이면 false")
    fun checkNicknameDuplicate_false() {
        assertThat(userService.checkNicknameDuplicate("unknown")).isFalse()
    }

    @Test
    @DisplayName("프로필 수정 성공")
    fun updateProfile_success() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val requestDto = UpdateProfileRequestDto("newNickname", "http://image.com/a.png", "안녕하세요")

        val userDto = userService.updateProfile(user.requiredId, requestDto)

        assertThat(userDto.nickname).isEqualTo("newNickname")
        val updated = userRepository.findById(user.requiredId).orElseThrow()
        assertThat(updated.nickname).isEqualTo("newNickname")
        assertThat(updated.profileImageUrl).isEqualTo("http://image.com/a.png")
        assertThat(updated.bio).isEqualTo("안녕하세요")
    }

    @Test
    @DisplayName("프로필 수정 성공 - 닉네임을 그대로 유지해도 중복 에러 안 남")
    fun updateProfile_success_sameNickname() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val requestDto = UpdateProfileRequestDto("nickname", null, "안녕하세요")

        val userDto = userService.updateProfile(user.requiredId, requestDto)

        assertThat(userDto.nickname).isEqualTo("nickname")
        assertThat(userRepository.findById(user.requiredId).orElseThrow().bio).isEqualTo("안녕하세요")
    }

    @Test
    @DisplayName("프로필 수정 실패 - 다른 유저의 닉네임과 중복")
    fun updateProfile_fail_duplicateNickname() {
        saveUser("other@test.com", "takenNickname", "password123")
        val user = saveUser("test@test.com", "nickname", "password123")
        val requestDto = UpdateProfileRequestDto("takenNickname", null, null)

        assertThatThrownBy { userService.updateProfile(user.requiredId, requestDto) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("409-2") })
    }

    @Test
    @DisplayName("프로필 수정 실패 - 존재하지 않는 유저")
    fun updateProfile_fail_userNotFound() {
        val requestDto = UpdateProfileRequestDto("nickname", null, null)

        assertThatThrownBy { userService.updateProfile(999_999L, requestDto) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("404-1") })
    }

    @Test
    @DisplayName("프로필 수정 - 이미지 URL이 바뀌면 옛날 이미지를 삭제함")
    fun updateProfile_deletesOldImage_whenImageUrlChanges() {
        val user = saveUser("test@test.com", "nickname", "password123")
        user.updateProfile(user.nickname, "http://image.com/old.png", user.bio)
        val requestDto = UpdateProfileRequestDto("nickname", "http://image.com/new.png", null)

        userService.updateProfile(user.requiredId, requestDto)

        verify(imageUploadService).deleteImage("http://image.com/old.png")
    }

    @Test
    @DisplayName("프로필 수정 - 이미지 URL이 그대로면 삭제 호출 안 함")
    fun updateProfile_doesNotDeleteImage_whenImageUrlUnchanged() {
        val user = saveUser("test@test.com", "nickname", "password123")
        user.updateProfile(user.nickname, "http://image.com/same.png", user.bio)
        val requestDto = UpdateProfileRequestDto("nickname", "http://image.com/same.png", null)

        userService.updateProfile(user.requiredId, requestDto)

        verify(imageUploadService, never()).deleteImage(anyString())
    }

    @Test
    @DisplayName("프로필 수정 - 원래 이미지가 없었으면 삭제 호출 안 함")
    fun updateProfile_doesNotDeleteImage_whenOldUrlWasNull() {
        val user = saveUser("test@test.com", "nickname", "password123")
        val requestDto = UpdateProfileRequestDto("nickname", "http://image.com/new.png", null)

        userService.updateProfile(user.requiredId, requestDto)

        verify(imageUploadService, never()).deleteImage(anyString())
    }

    @Test
    @DisplayName("관리자 승격 성공 - role이 ADMIN으로 바뀜")
    fun promoteToAdmin_success() {
        val user = saveUser("test@test.com", "nickname", "password123")

        val userDto = userService.promoteToAdmin(user.id)

        assertThat(userDto.id).isEqualTo(user.id)
        assertThat(userRepository.findById(user.requiredId).orElseThrow().role).isEqualTo("ADMIN")
    }

    @Test
    @DisplayName("관리자 승격 성공 - 로그인 시 발급되는 accessToken의 role 클레임에도 ADMIN이 반영됨")
    fun promoteToAdmin_success_reflectedInNewAccessToken() {
        val user = saveUser("test@test.com", "nickname", "password123")
        userService.promoteToAdmin(user.id)

        val result = userService.login(LoginRequestDto("test@test.com", "password123"))
        val claims = jwtProvider.parseClaims(result.accessToken)

        assertThat(claims.get("role", String::class.java)).isEqualTo("ADMIN")
    }

    @Test
    @DisplayName("관리자 승격 실패 - 존재하지 않는 유저")
    fun promoteToAdmin_fail_userNotFound() {
        assertThatThrownBy { userService.promoteToAdmin(999_999L) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ e -> assertThat((e as ServiceException).resultCode).isEqualTo("404-1") })
    }
}
