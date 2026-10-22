package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.review.service.ReviewService
import com.gamelog.nbe121423355.domain.user.dto.*
import com.gamelog.nbe121423355.domain.user.entity.RefreshToken
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.RefreshTokenRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFavoriteGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserPreferenceGameRepository
import com.gamelog.nbe121423355.domain.user.repository.UserPreferenceGenreRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.domain.user.service.TokenReissueService.ReissueResult
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.global.upload.ImageUploadService
import jakarta.transaction.Transactional
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class UserService
constructor(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val imageUploadService: ImageUploadService,
    private val tokenReissueService: TokenReissueService,
    private val reviewService: ReviewService,
    private val userFollowRepository: UserFollowRepository,
    private val userPreferenceGenreRepository: UserPreferenceGenreRepository,
    private val userPreferenceGameRepository: UserPreferenceGameRepository,
    private val userFavoriteGameRepository: UserFavoriteGameRepository,
) {
    // 로그인 결과를 Controller에 전달하기 위한 내부 운반용 record
    // refreshTokenExpiryDate: 쿠키 maxAge 계산용 (세션의 절대 만료 시각)
    @JvmRecord
    data class LoginResult(
        val user: UserDto,
        val accessToken: String,
        val refreshToken: String,
        val refreshTokenExpiryDate: LocalDateTime,
    ) {}

    // 새로 만든 세션의 refreshToken과 만료 시각 (비밀번호 변경 시 Controller가 쿠키로 내려줌)
    @JvmRecord
    data class IssuedSession(val refreshToken: String, val expiryDate: LocalDateTime) {}

    // 회원가입 메소드
    fun signUp(signUpDto: SignupRequestDto): UserDto {
        if (userRepository.existsByEmail(signUpDto.email)) {
            throw ServiceException("409-1", "이미 존재하는 이메일입니다.")
        }
        if (userRepository.existsByNickname(signUpDto.nickname)) {
            throw ServiceException("409-2", "이미 존재하는 닉네임입니다.")
        }
        val encodePassword = passwordEncoder.encode(signUpDto.password)
        val user = User(signUpDto.nickname, signUpDto.email, encodePassword)
        val saveUser: User = userRepository.save(user)
        return UserDto(saveUser)
    }

    // 로그인 메소드
    fun login(loginRequestDto: LoginRequestDto): LoginResult {
        val userOptional: Optional<User> = userRepository.findByEmail(loginRequestDto.email)
        if (userOptional.isEmpty()) {
            throw ServiceException("401-1", "이메일 또는 비밀번호가 일치하지 않습니다.")
        }
        val user = userOptional.get()
        if (!passwordEncoder.matches(loginRequestDto.password, user.password)) {
            throw ServiceException("401-1", "이메일 또는 비밀번호가 일치하지 않습니다.")
        }
        val accessToken = jwtProvider.generateAccessToken(user.id, user.role)
        val session = issueSession(user)
        return LoginResult(UserDto(user), accessToken, session.refreshToken, session.expiryDate)
    }

    // 새 로그인 세션 생성 (family 1개 + 첫 refreshToken)
    // 세션 만료 시각은 여기서 정해지고 Rotation 때도 그대로 유지 (절대 만료)
    private fun issueSession(user: User): IssuedSession {
        val familyId = UUID.randomUUID().toString()
        val jti = UUID.randomUUID().toString()
        val expiration = jwtProvider.newRefreshTokenExpiration()
        val expiryDate = expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        refreshTokenRepository.save(RefreshToken.create(user, familyId, jti, expiryDate))
        val refreshToken = jwtProvider.generateRefreshToken(user.id, familyId, jti, expiration)
        return IssuedSession(refreshToken, expiryDate)
    }

    // 로그아웃 메소드 (해당 세션 전체 폐기)
    @Transactional
    fun logout(refreshToken: String?) {
        if (jwtProvider.validateToken(refreshToken)) {
            val familyId = jwtProvider.getFamilyId(jwtProvider.parseClaims(refreshToken))
            if (familyId != null) {
                refreshTokenRepository.deleteByFamilyId(familyId)
            }
        }
    }

    // 토큰 갱신 메소드 (재발급 로직은 TokenReissueService에서 필터와 공유)
    // 교체된 refreshToken도 쿠키로 내려줘야 하므로 결과 전체를 Controller에 전달
    // 주의: @Transactional을 붙이면 재사용 감지 시 세션 삭제가 바깥 트랜잭션 롤백으로 취소됨
    fun refresh(refreshToken: String?): ReissueResult {
        return tokenReissueService.reissue(refreshToken)
    }

    // 내정보 가져오기 - 온보딩 확인 여부
    fun getMe(userId: Long): UserDto {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        return UserDto(user)
    }

    // 유저 정보 가져오기 (탈퇴한 유저는 존재하지 않는 유저로 취급)
    fun getUser(userId: Long?): PublicUserDto {
        val user =
            userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저입니다.") })
        return PublicUserDto(user)
    }

    // 온보딩 완료(선호 정보 저장 후 확정)
    @Transactional
    fun exitOnboarding(userId: Long): UserDto {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        user.completeOnboarding()
        return UserDto(user)
    }

    // 온보딩 건너뛰기
    @Transactional
    fun skipOnboarding(userId: Long): UserDto {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        user.completeOnboarding()
        return UserDto(user)
    }

    fun checkEmailDuplicate(email: String?): Boolean {
        return userRepository.existsByEmail(email)
    }

    fun checkNicknameDuplicate(nickname: String?): Boolean {
        return userRepository.existsByNickname(nickname)
    }

    @Transactional
    fun updateProfile(userId: Long, updateProfileRequestDto: UpdateProfileRequestDto): UserDto {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        if (
            !user.nickname.equals(updateProfileRequestDto.nickname) &&
                userRepository.existsByNickname(updateProfileRequestDto.nickname)
        ) {
            throw ServiceException("409-2", "이미 존재하는 닉네임 입니다.")
        }
        val oldProfileImageUrl = user.profileImageUrl
        user.updateProfile(
            updateProfileRequestDto.nickname,
            updateProfileRequestDto.profileImageUrl,
            updateProfileRequestDto.bio,
        )
        if (
            oldProfileImageUrl != null &&
                !oldProfileImageUrl.equals(updateProfileRequestDto.profileImageUrl)
        ) {
            imageUploadService.deleteImage(oldProfileImageUrl)
        }
        return UserDto(user)
    }

    // 비밀번호 변경(로그인 상태)
    // 모든 기기의 세션을 폐기한 뒤 현재 기기용 새 세션을 발급 (현재 기기는 로그인 유지, 다른 기기는 로그아웃)
    // accessToken은 그대로 유효하므로 새로 주지 않고, 새 refreshToken만 쿠키로 교체
    @Transactional
    fun changePassword(userId: Long, requestDto: ChangePasswordRequestDto): IssuedSession {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        if (!passwordEncoder.matches(requestDto.currentPassword, user.password)) {
            throw ServiceException("400-3", "현재 비밀번호가 일치하지 않습니다.")
        }
        if (passwordEncoder.matches(requestDto.newPassword, user.password)) {
            throw ServiceException("400-4", "새 비밀번호는 현재 비밀번호와 달라야 합니다.")
        }
        user.changePassword(passwordEncoder.encode(requestDto.newPassword))
        // 비밀번호 변경 시 모든 기기의 refreshToken 폐기
        refreshTokenRepository.deleteAllByUserId(userId)
        // deleteAllByUserId의 clearAutomatically로 user는 준영속 상태지만, 새 세션은 user의 id만 참조하므로 문제없음
        return issueSession(user)
    }

    @Transactional
    fun promoteToAdmin(userId: Long?): UserDto {
        // 탈퇴한 유저는 권한 변경 대상에서 제외
        val user =
            userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        user.promoteToAdmin()
        return UserDto(user)
    }

    // 회원탈퇴 (soft delete + 즉시 익명화)
    @Transactional
    fun withdraw(userId: Long?, requestDto: WithdrawRequestDto) {
        val user =
            userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow({ ServiceException("404-1", "존재하지 않는 유저 입니다.") })
        if ("ADMIN".equals(user.role)) {
            throw ServiceException("403-2", "관리자 계정은 탈퇴할 수 없습니다.")
        }
        if (!passwordEncoder.matches(requestDto.password, user.password)) {
            throw ServiceException("400-13", "비밀번호가 일치하지 않습니다.")
        }
        val oldProfileImageUrl = user.profileImageUrl
        // 익명화를 먼저: 마지막 refreshToken 삭제의 clearAutomatically 이후에는 user 변경이 반영되지 않음
        user.withdraw(passwordEncoder.encode(UUID.randomUUID().toString()))
        reviewService.hideAllByWithdrawnUser(checkNotNull(user.id))
        userFollowRepository.deleteAllByUserId(userId)
        userPreferenceGenreRepository.deleteByUser_Id(userId)
        userPreferenceGameRepository.deleteByUser_Id(userId)
        userFavoriteGameRepository.deleteAllByUserId(userId)
        refreshTokenRepository.deleteAllByUserId(userId)
        // 롤백되면 이미지만 지워지는 일이 없도록 커밋 후 삭제
        if (oldProfileImageUrl != null) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {

                    override fun afterCommit() {
                        imageUploadService.deleteImage(oldProfileImageUrl)
                    }
                }
            )
        }
    }
}
