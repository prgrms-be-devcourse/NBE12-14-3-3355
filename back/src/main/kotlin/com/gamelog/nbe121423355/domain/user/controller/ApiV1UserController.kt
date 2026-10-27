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
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "201-1: 회원가입 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 가입 정보 검증 실패 / 400-2: JSON 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "409", description = "409-1: 이메일 중복 / 409-2: 닉네임 중복 / 409-3: 동시 가입 제약 충돌", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun signUp(@Valid @RequestBody signupRequestDto: SignupRequestDto): RsData<UserDto> {
        val userDto = userService.signUp(signupRequestDto)
        return RsData("201-1", "회원가입 성공", userDto)
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "응답 data.accessToken을 Swagger Authorize에 입력해 보호된 API를 호출합니다. refreshToken은 응답 본문이 아닌 HttpOnly 쿠키로 설정됩니다. 재발급·로그아웃은 이 쿠키를 사용하며 refreshToken을 Bearer로 사용하면 안 됩니다. 쿠키의 Secure·SameSite 정책은 실행 환경에 따릅니다.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-1: 로그인 성공", useReturnTypeSchema = true, headers = [Header(name = "Set-Cookie", description = "HttpOnly refreshToken 쿠키. 브라우저가 저장하며 JavaScript로 읽을 수 없습니다.", schema = Schema(type = "string"))]),
        ApiResponse(responseCode = "400", description = "400-1: 입력 검증 실패 / 400-2: JSON 오류", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 이메일 또는 비밀번호 불일치", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun login(
        @Valid @RequestBody loginRequestDto: LoginRequestDto,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<LoginResponseDto> {
        val result = userService.login(loginRequestDto)
        val responseCookie =
            refreshTokenCookieFactory.create(result.refreshToken, result.refreshTokenExpiryDate)
        httpServletResponse.addResponseCookie(responseCookie)
        val body = LoginResponseDto(result.user, result.accessToken)
        return RsData("200-1", "로그인 성공", body)
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "요청의 refreshToken 쿠키를 폐기하고 Set-Cookie로 해당 쿠키를 제거합니다. 쿠키가 없어도 성공합니다. 이미 발급된 accessToken 자체를 즉시 무효화하는 API는 아닙니다.")
    @ApiResponse(responseCode = "200", description = "200-13: 로그아웃 성공", useReturnTypeSchema = true, headers = [Header(name = "Set-Cookie", description = "refreshToken 삭제 쿠키(maxAge=0)", schema = Schema(type = "string"))])
    fun logout(
        @CookieValue(value = RefreshTokenCookieFactory.COOKIE_NAME, required = false) refreshToken: String?,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<Void> {
        if (refreshToken != null) {
            userService.logout(refreshToken)
        }
        val responseCookie = refreshTokenCookieFactory.delete()
        httpServletResponse.addResponseCookie(responseCookie)
        return RsData("200-13", "로그아웃 성공")
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "accessToken 재발급",
        description =
            "HttpOnly refreshToken 쿠키로 새 accessToken을 발급하고 refreshToken도 교체합니다(Rotation). 동시 재발급 요청에는 직전 토큰에 대한 10초 유예가 있으며, 유예 밖의 재사용은 세션 폐기와 401-3으로 처리됩니다. 클라이언트는 응답의 새 쿠키와 accessToken을 사용해야 합니다. 새 accessToken으로 Swagger Authorize 값을 갱신하세요. 쿠키가 전송되지 않으면 재로그인이 필요하며 운영 쿠키를 로컬 HTTP에서 재사용할 수 있다고 가정하면 안 됩니다.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-2: 토큰 재발급 성공", useReturnTypeSchema = true, headers = [Header(name = "Set-Cookie", description = "교체된 HttpOnly refreshToken 쿠키", schema = Schema(type = "string"))]),
        ApiResponse(responseCode = "401", description = "401-2: refreshToken 누락·만료·폐기 등 인증 실패 / 401-3: 유예 시간 밖의 이전 토큰 재사용으로 세션 폐기", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun refresh(
        @CookieValue(value = RefreshTokenCookieFactory.COOKIE_NAME, required = false) refreshToken: String?,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<TokenResponseDto> {
        val result = userService.refresh(refreshToken)
        val responseCookie =
            refreshTokenCookieFactory.create(result.refreshToken, result.refreshTokenExpiryDate)
        httpServletResponse.addResponseCookie(responseCookie)
        val tokenResponseDto = TokenResponseDto(result.accessToken)
        return RsData("200-2", "토큰 재발급 성공", tokenResponseDto)
    }

    @GetMapping("/{userId}")
    @Operation(summary = "사용자 공개 정보 조회", description = "사용자 ID로 닉네임, 프로필 이미지와 자기소개를 조회합니다.")
    fun getOtherUser(@PathVariable userId: Long): RsData<PublicUserDto> {
        val userDto = userService.getUser(userId)
        return RsData("200-3", "유저 정보 조회 성공", userDto)
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun getMe(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<UserDto> {
        val userDto = userService.getMe(securityUser.id)
        return RsData("200-3", "내 정보 조회 성공", userDto)
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
        return RsData("200-10", "내 정보 수정 성공", userDto)
    }

    @PatchMapping("/me/onboarding")
    @Operation(summary = "온보딩 완료", security = [SecurityRequirement(name = "bearerAuth")])
    fun onboarding(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<UserDto> {
        val userDto = userService.exitOnboarding(securityUser.id)
        return RsData("200-4", "온보딩 완료", userDto)
    }

    @PatchMapping("/me/onboarding/skip")
    @Operation(summary = "온보딩 건너뛰기", security = [SecurityRequirement(name = "bearerAuth")])
    fun onboardingSkip(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<UserDto> {
        val userDto = userService.skipOnboarding(securityUser.id)
        return RsData("200-5", "온보딩 스킵", userDto)
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
        return RsData("200-6", "선호 장르 저장 성공", result)
    }

    @GetMapping("/me/preferred-genres")
    @Operation(summary = "선호 장르 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun getPreferredGenres(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<List<PreferredGenreResponseDto>> {
        val result =
            userPreferenceGenreService.getPreferredGenres(securityUser.id)
        return RsData("200-7", "선호 장르 조회 성공", result)
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
        return RsData("200-8", "선호 게임 저장 성공", result)
    }

    @GetMapping("/me/preferred-games")
    @Operation(summary = "선호 게임 조회", security = [SecurityRequirement(name = "bearerAuth")])
    fun getPreferredGames(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser
    ): RsData<List<PreferredGameResponseDto>> {
        val result = userPreferenceGameService.getPreferredGames(securityUser.id)
        return RsData("200-9", "선호 게임 조회 성공", result)
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
        return RsData("200-21", "비밀번호 변경 성공")
    }

    @DeleteMapping("/me")
    @Operation(
        summary = "회원탈퇴",
        description = "비밀번호를 확인한 뒤 계정을 익명화하고 모든 기기에서 로그아웃합니다. 관리자 계정은 탈퇴할 수 없습니다.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "200-19: 계정 익명화·세션 정리 성공", useReturnTypeSchema = true),
        ApiResponse(responseCode = "400", description = "400-1: 입력 검증 / 400-13: 비밀번호 불일치", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "401", description = "401-1: 로그인 필요", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "403", description = "403-2: 관리자 계정은 탈퇴 불가", content = [Content(schema = Schema(implementation = RsData::class))]),
        ApiResponse(responseCode = "404", description = "404-1: 활성 사용자 없음", content = [Content(schema = Schema(implementation = RsData::class))]),
    ])
    fun withdraw(
        @Parameter(hidden = true) @AuthenticationPrincipal securityUser: SecurityUser,
        @Valid @RequestBody withdrawRequestDto: WithdrawRequestDto,
        @Parameter(hidden = true) httpServletResponse: HttpServletResponse,
    ): RsData<Void> {
        userService.withdraw(securityUser.id, withdrawRequestDto)
        val responseCookie = refreshTokenCookieFactory.delete()
        httpServletResponse.addResponseCookie(responseCookie)
        return RsData("200-19", "회원탈퇴 성공")
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
