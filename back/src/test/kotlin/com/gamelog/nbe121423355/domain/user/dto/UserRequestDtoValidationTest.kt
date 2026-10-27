package com.gamelog.nbe121423355.domain.user.dto

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

// user 요청 DTO의 검증 규칙 19개를 규칙마다 한 번씩 위반시켜 확인
class UserRequestDtoValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun violationsOf(request: Any): List<String> =
        validator.validate(request)
            .map { "${it.propertyPath}:${it.constraintDescriptor.annotation.annotationClass.simpleName}" }
            .sorted()

    @ParameterizedTest(name = "{0}")
    @MethodSource("validRequests")
    @DisplayName("규칙을 지킨 요청은 위반이 없다")
    fun validRequestHasNoViolations(description: String, request: Any) {
        assertThat(violationsOf(request)).isEmpty()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    @DisplayName("규칙을 어긴 요청은 해당 필드의 해당 규칙만 위반한다")
    fun invalidRequestViolatesOnlyThatRule(description: String, request: Any, expected: List<String>) {
        assertThat(violationsOf(request)).isEqualTo(expected)
    }

    companion object {
        private const val PASSWORD = "password123"
        private val BYTES_72 = "a".repeat(72)
        private val BYTES_73 = "a".repeat(73)

        // 한글은 UTF-8에서 3바이트라 글자 수가 아니라 바이트 수로 막히는지 확인
        private val KOREAN_72_BYTES = "가".repeat(24)
        private val KOREAN_75_BYTES = "가".repeat(25)

        @JvmStatic
        fun validRequests(): List<Arguments> =
            listOf(
                Arguments.of("회원가입", SignupRequestDto("게임마스터", "player@example.com", PASSWORD)),
                Arguments.of("회원가입 - 비밀번호 8자(최소)", SignupRequestDto("게임마스터", "player@example.com", "pass1234")),
                Arguments.of("회원가입 - 비밀번호 72바이트(최대)", SignupRequestDto("게임마스터", "player@example.com", BYTES_72)),
                Arguments.of(
                    "회원가입 - 한글 비밀번호 24자 = 72바이트",
                    SignupRequestDto("게임마스터", "player@example.com", KOREAN_72_BYTES),
                ),
                Arguments.of("로그인", LoginRequestDto("player@example.com", PASSWORD)),
                Arguments.of("비밀번호 변경", ChangePasswordRequestDto(PASSWORD, "newPassword123")),
                Arguments.of("회원탈퇴", WithdrawRequestDto(PASSWORD)),
                Arguments.of("프로필 수정 - 사진·자기소개 없음", UpdateProfileRequestDto("게임마스터", null, null)),
                Arguments.of("선호 게임", PreferredGameRequestDto(listOf(1L))),
                Arguments.of("선호 장르", PreferredGenreRequestDto(listOf(1L))),
            )

        @JvmStatic
        fun invalidRequests(): List<Arguments> =
            listOf(
                // 회원가입 (6)
                Arguments.of(
                    "회원가입 - 닉네임 공백",
                    SignupRequestDto("   ", "player@example.com", PASSWORD),
                    listOf("nickname:NotBlank"),
                ),
                Arguments.of(
                    "회원가입 - 이메일 빈 값",
                    SignupRequestDto("게임마스터", "", PASSWORD),
                    listOf("email:NotBlank"),
                ),
                Arguments.of(
                    "회원가입 - 이메일 형식 아님",
                    SignupRequestDto("게임마스터", "not-an-email", PASSWORD),
                    listOf("email:Email"),
                ),
                Arguments.of(
                    "회원가입 - 비밀번호 없음",
                    SignupRequestDto("게임마스터", "player@example.com", null),
                    listOf("password:NotBlank"),
                ),
                Arguments.of(
                    "회원가입 - 비밀번호 7자",
                    SignupRequestDto("게임마스터", "player@example.com", "pass123"),
                    listOf("password:Size"),
                ),
                Arguments.of(
                    "회원가입 - 비밀번호 73바이트",
                    SignupRequestDto("게임마스터", "player@example.com", BYTES_73),
                    listOf("password:MaxByteLength"),
                ),
                Arguments.of(
                    "회원가입 - 한글 비밀번호 25자 = 75바이트",
                    SignupRequestDto("게임마스터", "player@example.com", KOREAN_75_BYTES),
                    listOf("password:MaxByteLength"),
                ),
                // 로그인 (3)
                Arguments.of("로그인 - 이메일 없음", LoginRequestDto(null, PASSWORD), listOf("email:NotBlank")),
                Arguments.of(
                    "로그인 - 비밀번호 없음",
                    LoginRequestDto("player@example.com", null),
                    listOf("password:NotBlank"),
                ),
                Arguments.of(
                    "로그인 - 비밀번호 73바이트",
                    LoginRequestDto("player@example.com", BYTES_73),
                    listOf("password:MaxByteLength"),
                ),
                // 비밀번호 변경 (5)
                Arguments.of(
                    "비밀번호 변경 - 현재 비밀번호 없음",
                    ChangePasswordRequestDto(null, "newPassword123"),
                    listOf("currentPassword:NotBlank"),
                ),
                Arguments.of(
                    "비밀번호 변경 - 현재 비밀번호 73바이트",
                    ChangePasswordRequestDto(BYTES_73, "newPassword123"),
                    listOf("currentPassword:MaxByteLength"),
                ),
                Arguments.of(
                    "비밀번호 변경 - 새 비밀번호 없음",
                    ChangePasswordRequestDto(PASSWORD, null),
                    listOf("newPassword:NotBlank"),
                ),
                Arguments.of(
                    "비밀번호 변경 - 새 비밀번호 7자",
                    ChangePasswordRequestDto(PASSWORD, "pass123"),
                    listOf("newPassword:Size"),
                ),
                Arguments.of(
                    "비밀번호 변경 - 새 비밀번호 73바이트",
                    ChangePasswordRequestDto(PASSWORD, BYTES_73),
                    listOf("newPassword:MaxByteLength"),
                ),
                // 회원탈퇴 (2)
                Arguments.of("회원탈퇴 - 비밀번호 없음", WithdrawRequestDto(null), listOf("password:NotBlank")),
                Arguments.of(
                    "회원탈퇴 - 비밀번호 73바이트",
                    WithdrawRequestDto(BYTES_73),
                    listOf("password:MaxByteLength"),
                ),
                // 프로필 수정 (1)
                Arguments.of(
                    "프로필 수정 - 닉네임 빈 값",
                    UpdateProfileRequestDto("", null, null),
                    listOf("nickname:NotBlank"),
                ),
                // 선호 게임·장르 (2)
                Arguments.of(
                    "선호 게임 - 빈 목록",
                    PreferredGameRequestDto(emptyList()),
                    listOf("gameIds:NotEmpty"),
                ),
                Arguments.of(
                    "선호 장르 - 목록 없음",
                    PreferredGenreRequestDto(null),
                    listOf("genreIds:NotEmpty"),
                ),
            )
    }
}
