package com.gamelog.nbe121423355.domain.user.controller

import com.gamelog.nbe121423355.domain.user.dto.*
import com.gamelog.nbe121423355.domain.user.service.UserPreferenceGameService
import com.gamelog.nbe121423355.domain.user.service.UserPreferenceGenreService
import com.gamelog.nbe121423355.domain.user.service.UserService
import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.SecurityUser
import com.gamelog.nbe121423355.global.security.jwt.RefreshTokenCookieFactory
import com.gamelog.nbe121423355.global.security.jwt.addResponseCookie
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "api-v1-user-controller", description = "회원가입·로그인·프로필·온보딩 API")
class ApiV1UserController(
    private val userService: UserService,
    private val userPreferenceGenreService: UserPreferenceGenreService,
    private val userPreferenceGameService: UserPreferenceGameService,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
) {
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "닉네임, 이메일과 비밀번호로 일반 사용자 계정을 생성합니다.")
    fun signUp(@Valid @RequestBody signupRequestDto: SignupRequestDto): RsData<UserDto> {
        val userDto = userService.signUp(signupRequestDto)
        return RsData<UserDto>("201-1", "회원가입 성공", userDto)
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "accessToken을 응답하고 refreshToken을 HttpOnly 쿠키로 설정합니다.")
    fun login(
        @Valid @RequestBody loginRequestDto: LoginRequestDto,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<LoginResponseDto> {
        val result = userService.login(loginRequestDto)
        val responseCookie =
            refreshTokenCookieFactory.create(result.refreshToken, result.refreshTokenExpiryDate)
        httpServletResponse.addResponseCookie(responseCookie)
        val body = LoginResponseDto(result.user, result.accessToken)
        return RsData<LoginResponseDto>("200-1", "로그인 성공", body)
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "refreshToken을 폐기하고 인증 쿠키를 제거합니다.")
    fun logout(
        @CookieValue(value = "refreshToken", required = false) refreshToken: String?,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<Void> {
        if (refreshToken != null) {
            userService.logout(refreshToken)
        }
        val responseCookie = refreshTokenCookieFactory.delete()
        httpServletResponse.addResponseCookie(responseCookie)
        return RsData<Void>("200-13", "로그아웃 성공")
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "accessToken 재발급",
        description =
            "HttpOnly refreshToken 쿠키를 사용해 새로운 accessToken을 발급하고, refreshToken을 교체한 쿠키로 다시 설정합니다.",
    )
    fun refresh(
        @CookieValue(value = "refreshToken", required = false) refreshToken: String?,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<TokenResponseDto> {
        val result = userService.refresh(refreshToken)
        val responseCookie =
            refreshTokenCookieFactory.create(result.refreshToken, result.refreshTokenExpiryDate)
        httpServletResponse.addResponseCookie(responseCookie)
        val tokenResponseDto = TokenResponseDto(result.accessToken)
        return RsData<TokenResponseDto>("200-2", "토큰 재발급 성공", tokenResponseDto)
    }

    @GetMapping("/{userId}")
    @Operation(summary = "사용자 공개 정보 조회", description = "사용자 ID로 닉네임, 프로필 이미지와 자기소개를 조회합니다.")
    fun getOtherUser(@PathVariable userId: Long): RsData<PublicUserDto> {
        val userDto = userService.getUser(userId)
        return RsData<PublicUserDto>("200-3", "유저 정보 조회 성공", userDto)
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun getMe(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<UserDto> {
        val userDto = userService.getMe(securityUser.id)
        return RsData<UserDto>("200-3", "내 정보 조회 성공", userDto)
    }

    @PatchMapping("/me")
    @Operation(
        summary = "내 프로필 수정",
        description = "닉네임, 프로필 이미지와 자기소개를 수정합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun updateProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid @RequestBody updateProfileRequestDto: UpdateProfileRequestDto,
    ): RsData<UserDto> {
        val userDto = userService.updateProfile(securityUser.id, updateProfileRequestDto)
        return RsData<UserDto>("200-10", "내 정보 수정 성공", userDto)
    }

    @PatchMapping("/me/onboarding")
    @Operation(summary = "온보딩 완료", security = [SecurityRequirement(name = "bearerAuth")])
    fun onboarding(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<UserDto> {
        val userDto = userService.exitOnboarding(securityUser.id)
        return RsData<UserDto>("200-4", "온보딩 완료", userDto)
    }

    @PatchMapping("/me/onboarding/skip")
    @Operation(summary = "온보딩 건너뛰기", security = [SecurityRequirement(name = "bearerAuth")])
    fun onboardingSkip(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<UserDto> {
        val userDto = userService.skipOnboarding(securityUser.id)
        return RsData<UserDto>("200-5", "온보딩 스킵", userDto)
    }

    @PutMapping("/me/preferred-genres")
    @Operation(
        summary = "선호 장르 저장",
        description = "온보딩에서 선택한 선호 장르 목록을 교체합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun updatePreferredGenres(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid @RequestBody requestDto: PreferredGenreRequestDto,
    ): RsData<List<PreferredGenreResponseDto>> {
        val result =
            userPreferenceGenreService.updatePreferredGenres(
                securityUser.id,
                requireNotNull(requestDto.genreIds),
            )
        return RsData<List<PreferredGenreResponseDto>>("200-6", "선호 장르 저장 성공", result)
    }

    @GetMapping("/me/preferred-genres")
    @Operation(summary = "선호 장르 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun getPreferredGenres(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<List<PreferredGenreResponseDto>> {
        val result =
            userPreferenceGenreService.getPreferredGenres(securityUser.id)
        return RsData<List<PreferredGenreResponseDto>>("200-7", "선호 장르 조회 성공", result)
    }

    @PutMapping("/me/preferred-games")
    @Operation(
        summary = "선호 게임 저장",
        description = "온보딩에서 선택한 선호 게임 목록을 교체합니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun updatePreferredGames(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid @RequestBody requestDto: PreferredGameRequestDto,
    ): RsData<List<PreferredGameResponseDto>> {
        val result =
            userPreferenceGameService.updatePreferredGames(
                securityUser.id,
                requireNotNull(requestDto.gameIds),
            )
        return RsData<List<PreferredGameResponseDto>>("200-8", "선호 게임 저장 성공", result)
    }

    @GetMapping("/me/preferred-games")
    @Operation(summary = "선호 게임 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun getPreferredGames(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<List<PreferredGameResponseDto>> {
        val result = userPreferenceGameService.getPreferredGames(securityUser.id)
        return RsData<List<PreferredGameResponseDto>>("200-9", "선호 게임 조회 성공", result)
    }

    @PatchMapping("/me/password")
    @Operation(
        summary = "비밀번호 변경",
        description =
            "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. 다른 기기는 로그아웃되고, 현재 기기는 새 refreshToken 쿠키로 로그인이 유지됩니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun changePassword(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid @RequestBody changePasswordRequestDto: ChangePasswordRequestDto,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<Void> {
        val session = userService.changePassword(securityUser.id, changePasswordRequestDto)
        val responseCookie =
            refreshTokenCookieFactory.create(session.refreshToken, session.expiryDate)
        httpServletResponse.addResponseCookie(responseCookie)
        return RsData<Void>("200-14", "비밀번호 변경 성공")
    }

    @DeleteMapping("/me")
    @Operation(
        summary = "회원탈퇴",
        description = "비밀번호를 확인한 뒤 계정을 익명화하고 모든 기기에서 로그아웃합니다. 관리자 계정은 탈퇴할 수 없습니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid @RequestBody withdrawRequestDto: WithdrawRequestDto,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<Void> {
        userService.withdraw(securityUser.id, withdrawRequestDto)
        val responseCookie = refreshTokenCookieFactory.delete()
        httpServletResponse.addResponseCookie(responseCookie)
        return RsData<Void>("200-19", "회원탈퇴 성공")
    }

    @GetMapping("/check-email")
    @Operation(summary = "이메일 중복 확인", description = "true이면 이미 사용 중인 이메일입니다.")
    fun checkEmailDuplicate(@RequestParam email: String?): RsData<Boolean> {
        val result = userService.checkEmailDuplicate(email)
        return RsData("200-11", "이메일 중복 확인 성공", result)
    }

    @GetMapping("/check-nickname")
    @Operation(summary = "닉네임 중복 확인", description = "true이면 이미 사용 중인 닉네임입니다.")
    fun chekNicknameDuplicate(@RequestParam nickname: String?): RsData<Boolean> {
        val result = userService.checkNicknameDuplicate(nickname)
        return RsData("200-12", "닉네임 중복 확인 성공", result)
    }
}
