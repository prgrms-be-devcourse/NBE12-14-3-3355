package com.gamelog.nbe121423355.domain.user.entity

import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UserTest {
    @Test
    @DisplayName("회원가입 생성자 - USER 권한, 온보딩 미완료, 활성 상태로 시작한다")
    fun signupStartsAsActiveUserWithoutOnboarding() {
        val user = User("닉네임", "user@test.com", "encoded")

        assertThat(user.role).isEqualTo("USER")
        assertThat(user.isOnboardingCompleted).isFalse()
        assertThat(user.deletedAt).isNull()
        assertThat(user.isDeleted).isFalse()
    }

    @Test
    @DisplayName("회원가입 생성자 - 필수값이 null이면 NullPointerException")
    fun signupRejectsNullRequiredFields() {
        assertThatThrownBy { User(null, "user@test.com", "encoded") }
            .isInstanceOf(NullPointerException::class.java)
        assertThatThrownBy { User("닉네임", null, "encoded") }
            .isInstanceOf(NullPointerException::class.java)
        assertThatThrownBy { User("닉네임", "user@test.com", null) }
            .isInstanceOf(NullPointerException::class.java)
    }

    @Test
    @DisplayName("completeOnboarding - 온보딩 완료 상태로 바뀐다")
    fun completeOnboarding() {
        val user = User("닉네임", "user@test.com", "encoded")

        user.completeOnboarding()

        assertThat(user.isOnboardingCompleted).isTrue()
    }

    @Test
    @DisplayName("updateProfile - 닉네임, 프로필 이미지, 소개를 바꾼다")
    fun updateProfile() {
        val user = User("닉네임", "user@test.com", "encoded")

        user.updateProfile("새닉네임", "https://image.test/a.png", "소개")

        assertThat(user.nickname).isEqualTo("새닉네임")
        assertThat(user.profileImageUrl).isEqualTo("https://image.test/a.png")
        assertThat(user.bio).isEqualTo("소개")
    }

    @Test
    @DisplayName("updateProfile - 이미지와 소개에 null을 넘기면 비운다")
    fun updateProfileClearsOptionalFields() {
        val user = activeUser()

        user.updateProfile("닉네임", null, null)

        assertThat(user.profileImageUrl).isNull()
        assertThat(user.bio).isNull()
    }

    @Test
    @DisplayName("updateProfile - 닉네임이 null이면 NullPointerException이고 기존 값은 유지된다")
    fun updateProfileRejectsNullNickname() {
        val user = activeUser()

        assertThatThrownBy { user.updateProfile(null, null, null) }
            .isInstanceOf(NullPointerException::class.java)
        assertThat(user.nickname).isEqualTo("닉네임")
    }

    @Test
    @DisplayName("changePassword - 암호화된 새 비밀번호로 바꾼다")
    fun changePassword() {
        val user = activeUser()

        user.changePassword("new-encoded")

        assertThat(user.password).isEqualTo("new-encoded")
    }

    @Test
    @DisplayName("changePassword - null이면 NullPointerException")
    fun changePasswordRejectsNull() {
        val user = activeUser()

        assertThatThrownBy { user.changePassword(null) }
            .isInstanceOf(NullPointerException::class.java)
        assertThat(user.password).isEqualTo("encoded")
    }

    @Test
    @DisplayName("promoteToAdmin - ADMIN 권한으로 바뀐다")
    fun promoteToAdmin() {
        val user = activeUser()

        user.promoteToAdmin()

        assertThat(user.role).isEqualTo("ADMIN")
    }

    @Test
    @DisplayName("withdraw - 이메일과 닉네임을 id 기반 값으로 익명화하고 프로필을 비운다")
    fun withdrawAnonymizesPersonalData() {
        val user = activeUser()

        user.withdraw("random-encoded")

        assertThat(user.email).isEqualTo("deleted_1@deleted.local")
        assertThat(user.nickname).isEqualTo("탈퇴한사용자_1")
        assertThat(user.password).isEqualTo("random-encoded")
        assertThat(user.profileImageUrl).isNull()
        assertThat(user.bio).isNull()
    }

    @Test
    @DisplayName("withdraw - 탈퇴 시각이 기록되고 탈퇴 상태가 된다")
    fun withdrawMarksDeleted() {
        val user = activeUser()
        val before = LocalDateTime.now()

        user.withdraw("random-encoded")

        assertThat(user.isDeleted).isTrue()
        assertThat(user.deletedAt).isBetween(before, LocalDateTime.now())
    }

    private fun activeUser() =
        User(
            id = 1L,
            email = "user@test.com",
            password = "encoded",
            nickname = "닉네임",
            profileImageUrl = "https://image.test/old.png",
            bio = "기존 소개",
            onboardingCompleted = true,
            role = "USER",
            deletedAt = null,
        )
}
