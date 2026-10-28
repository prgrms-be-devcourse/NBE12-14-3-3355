package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFollow
import com.gamelog.nbe121423355.domain.user.entity.UserFollowId
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

@ExtendWith(MockitoExtension::class)
class UserFollowServiceTest {
    @Mock
    private lateinit var userFollowRepository: UserFollowRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var notificationService: NotificationService

    @InjectMocks
    private lateinit var userFollowService: UserFollowService

    @Test
    @DisplayName("사용자를 팔로우하면 팔로우 관계가 저장된다")
    fun followSavesRelationship() {
        // given: 로그인 사용자와 팔로우 대상 사용자를 준비
        val follower = mockUser(LOGIN_USER_ID, "로그인 사용자", null)
        val followee = mockUser(TARGET_USER_ID, "팔로우 대상", "profile.png")
        val followId = UserFollowId(LOGIN_USER_ID, TARGET_USER_ID)
        `when`(userRepository.findByIdAndDeletedAtIsNull(LOGIN_USER_ID)).thenReturn(follower)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(followee)
        `when`(userFollowRepository.existsById(followId)).thenReturn(false)

        // when: 대상 사용자를 팔로우
        val response = userFollowService.follow(LOGIN_USER_ID, TARGET_USER_ID)

        // then: 팔로우 관계가 저장되고 최종 상태가 반환되는지 확인
        val captor = ArgumentCaptor.forClass(UserFollow::class.java)
        verify(userFollowRepository).save(captor.capture())
        assertThat(captor.value.follower).isEqualTo(follower)
        assertThat(captor.value.followee).isEqualTo(followee)
        assertThat(response.targetUserId).isEqualTo(TARGET_USER_ID)
        assertThat(response.followed).isTrue()
    }

    @Test
    @DisplayName("이미 팔로우 중이면 팔로우 관계를 중복 저장하지 않는다")
    fun followDoesNotSaveDuplicateRelationship() {
        // given: 이미 존재하는 팔로우 관계를 준비
        val follower = mockUser(LOGIN_USER_ID, "로그인 사용자", null)
        val followee = mockUser(TARGET_USER_ID, "팔로우 대상", null)
        val followId = UserFollowId(LOGIN_USER_ID, TARGET_USER_ID)
        `when`(userRepository.findByIdAndDeletedAtIsNull(LOGIN_USER_ID)).thenReturn(follower)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(followee)
        `when`(userFollowRepository.existsById(followId)).thenReturn(true)

        // when: 동일한 사용자를 다시 팔로우
        val response = userFollowService.follow(LOGIN_USER_ID, TARGET_USER_ID)

        // then: 관계를 추가로 저장하지 않고 팔로우 상태를 반환하는지 확인
        verify(userFollowRepository, never()).save(any(UserFollow::class.java))
        assertThat(response.followed).isTrue()
    }

    @Test
    @DisplayName("팔로우 중인 사용자를 언팔로우하면 관계가 삭제된다")
    fun unfollowDeletesRelationship() {
        // given: 팔로우 중인 사용자 관계를 준비
        val targetUser = mockUser(TARGET_USER_ID, "팔로우 대상", null)
        val followId = UserFollowId(LOGIN_USER_ID, TARGET_USER_ID)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(targetUser)
        `when`(userFollowRepository.existsById(followId)).thenReturn(true)

        // when: 대상 사용자를 언팔로우
        val response = userFollowService.unfollow(LOGIN_USER_ID, TARGET_USER_ID)

        // then: 팔로우 관계가 삭제되고 최종 상태가 반환되는지 확인
        verify(userFollowRepository).deleteById(followId)
        assertThat(response.targetUserId).isEqualTo(TARGET_USER_ID)
        assertThat(response.followed).isFalse()
    }

    @Test
    @DisplayName("관계가 없는 사용자를 언팔로우해도 성공한다")
    fun unfollowSucceedsWithoutRelationship() {
        // given: 존재하지만 팔로우하지 않은 사용자를 준비
        val targetUser = mockUser(TARGET_USER_ID, "팔로우 대상", null)
        val followId = UserFollowId(LOGIN_USER_ID, TARGET_USER_ID)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(targetUser)
        `when`(userFollowRepository.existsById(followId)).thenReturn(false)

        // when: 대상 사용자를 언팔로우
        val response = userFollowService.unfollow(LOGIN_USER_ID, TARGET_USER_ID)

        // then: 삭제를 실행하지 않고 미팔로우 상태를 반환하는지 확인
        verify(userFollowRepository, never()).deleteById(followId)
        assertThat(response.followed).isFalse()
    }

    @Test
    @DisplayName("자기 자신은 팔로우할 수 없다")
    fun followRejectsSelfFollow() {
        // given: 로그인 사용자와 대상 사용자 ID를 동일하게 준비
        val sameUserId = LOGIN_USER_ID

        // when: 자기 자신을 팔로우하는 기능을 실행
        // then: 잘못된 요청 예외가 발생하고 저장소를 사용하지 않는지 확인
        assertThatThrownBy { userFollowService.follow(sameUserId, sameUserId) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ exception ->
                assertThat((exception as ServiceException).resultCode).isEqualTo("400-6")
            })
        verifyNoInteractions(userRepository, userFollowRepository)
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 팔로우할 수 없다")
    fun followRejectsMissingTargetUser() {
        // given: 로그인 사용자만 존재하도록 준비
        val follower = mockUser(LOGIN_USER_ID, "로그인 사용자", null)
        `when`(userRepository.findByIdAndDeletedAtIsNull(LOGIN_USER_ID)).thenReturn(follower)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(null)

        // when: 존재하지 않는 사용자를 팔로우하는 기능을 실행
        // then: 사용자 조회 예외가 발생하고 팔로우 관계를 저장하지 않는지 확인
        assertThatThrownBy { userFollowService.follow(LOGIN_USER_ID, TARGET_USER_ID) }
            .isInstanceOf(ServiceException::class.java)
            .satisfies({ exception ->
                assertThat((exception as ServiceException).resultCode).isEqualTo("404-1")
            })
        verify(userFollowRepository, never()).save(any(UserFollow::class.java))
    }

    @Test
    @DisplayName("로그인 사용자는 팔로잉 목록의 버튼 상태를 함께 조회한다")
    fun getFollowingIncludesLoginUserState() {
        // given: 조회 대상의 팔로잉 관계와 로그인 사용자의 팔로우 상태를 준비
        val targetUser = mockUser(TARGET_USER_ID, "조회 대상", null)
        val displayedUser = mockUser(LIST_USER_ID, "목록 사용자", "profile.png")
        val userFollow = mock(UserFollow::class.java)
        val followedAt = LocalDateTime.of(2026, 9, 22, 10, 30)
        val requestedPage = PageRequest.of(0, 10)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(targetUser)
        `when`(userFollow.followee).thenReturn(displayedUser)
        `when`(userFollow.createdDate).thenReturn(followedAt)
        `when`(userFollowRepository.findAllByFollower_Id(eq(TARGET_USER_ID), any(Pageable::class.java)))
            .thenReturn(PageImpl(listOf(userFollow), requestedPage, 1))
        `when`(userFollowRepository.findFolloweeIdsByFollowerIdAndFolloweeIds(LOGIN_USER_ID, listOf(LIST_USER_ID)))
            .thenReturn(mutableListOf(LIST_USER_ID))

        // when: 로그인 사용자 상태로 Following 목록을 조회
        val response = userFollowService.getFollowing(LOGIN_USER_ID, TARGET_USER_ID, 0, 10)

        // then: 최신순 정렬과 팔로우 버튼 상태가 반환되는지 확인
        val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(userFollowRepository).findAllByFollower_Id(eq(TARGET_USER_ID), pageableCaptor.capture())
        val createdDateOrder = pageableCaptor.value.sort.getOrderFor("createdDate")
        assertThat(createdDateOrder).isNotNull()
        assertThat(createdDateOrder?.direction).isEqualTo(Sort.Direction.DESC)
        assertThat(response.users).hasSize(1)
        assertThat(response.users.first().userId).isEqualTo(LIST_USER_ID)
        assertThat(response.users.first().followedAt).isEqualTo(followedAt)
        assertThat(response.users.first().followedByMe).isTrue()
        assertThat(response.users.first().me).isFalse()
    }

    @Test
    @DisplayName("비로그인 사용자는 팔로워 목록을 버튼 상태 없이 조회한다")
    fun getFollowersWithoutLoginUserReturnsNoButtonState() {
        // given: 공개 조회할 팔로워 관계를 준비
        val targetUser = mockUser(TARGET_USER_ID, "조회 대상", null)
        val displayedUser = mockUser(LIST_USER_ID, "목록 사용자", null)
        val userFollow = mock(UserFollow::class.java)
        val requestedPage = PageRequest.of(0, 10)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(targetUser)
        `when`(userFollow.follower).thenReturn(displayedUser)
        `when`(userFollowRepository.findAllByFollowee_Id(eq(TARGET_USER_ID), any(Pageable::class.java)))
            .thenReturn(PageImpl(listOf(userFollow), requestedPage, 1))

        // when: 인증 정보 없이 Followers 목록을 조회
        val response = userFollowService.getFollowers(null, TARGET_USER_ID, 0, 10)

        // then: 팔로우 상태 조회 없이 버튼 상태가 모두 false인지 확인
        verify(userFollowRepository, never())
            .findFolloweeIdsByFollowerIdAndFolloweeIds(any(Long::class.java), anyList())
        assertThat(response.users).hasSize(1)
        assertThat(response.users.first().followedByMe).isFalse()
        assertThat(response.users.first().me).isFalse()
    }

    @Test
    @DisplayName("로그인 사용자가 목록에 포함되면 본인 상태로 반환한다")
    fun getFollowingMarksLoginUserAsMe() {
        // given: 조회 대상 사용자가 로그인 사용자를 팔로우하도록 준비
        val targetUser = mockUser(TARGET_USER_ID, "조회 대상", null)
        val loginUser = mockUser(LOGIN_USER_ID, "로그인 사용자", null)
        val userFollow = mock(UserFollow::class.java)
        val requestedPage = PageRequest.of(0, 10)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(targetUser)
        `when`(userFollow.followee).thenReturn(loginUser)
        `when`(userFollowRepository.findAllByFollower_Id(eq(TARGET_USER_ID), any(Pageable::class.java)))
            .thenReturn(PageImpl(listOf(userFollow), requestedPage, 1))
        `when`(userFollowRepository.findFolloweeIdsByFollowerIdAndFolloweeIds(LOGIN_USER_ID, listOf(LOGIN_USER_ID)))
            .thenReturn(mutableListOf())

        // when: 로그인 사용자 상태로 Following 목록을 조회
        val response = userFollowService.getFollowing(LOGIN_USER_ID, TARGET_USER_ID, 0, 10)

        // then: 목록의 로그인 사용자가 본인으로 표시되는지 확인
        assertThat(response.users).hasSize(1)
        assertThat(response.users.first().me).isTrue()
        assertThat(response.users.first().followedByMe).isFalse()
    }

    @Test
    @DisplayName("로그인 사용자가 팔로우하지 않은 사용자는 미팔로우 상태로 반환한다")
    fun getFollowingMarksUnfollowedUser() {
        // given: 로그인 사용자가 팔로우하지 않은 목록 사용자를 준비
        val targetUser = mockUser(TARGET_USER_ID, "조회 대상", null)
        val displayedUser = mockUser(LIST_USER_ID, "목록 사용자", null)
        val userFollow = mock(UserFollow::class.java)
        val requestedPage = PageRequest.of(0, 10)
        `when`(userRepository.findByIdAndDeletedAtIsNull(TARGET_USER_ID)).thenReturn(targetUser)
        `when`(userFollow.followee).thenReturn(displayedUser)
        `when`(userFollowRepository.findAllByFollower_Id(eq(TARGET_USER_ID), any(Pageable::class.java)))
            .thenReturn(PageImpl(listOf(userFollow), requestedPage, 1))
        `when`(userFollowRepository.findFolloweeIdsByFollowerIdAndFolloweeIds(LOGIN_USER_ID, listOf(LIST_USER_ID)))
            .thenReturn(mutableListOf())

        // when: 로그인 사용자 상태로 Following 목록을 조회
        val response = userFollowService.getFollowing(LOGIN_USER_ID, TARGET_USER_ID, 0, 10)

        // then: 본인이 아니며 팔로우하지 않은 상태로 표시되는지 확인
        assertThat(response.users).hasSize(1)
        assertThat(response.users.first().me).isFalse()
        assertThat(response.users.first().followedByMe).isFalse()
    }

    private fun mockUser(userId: Long, nickname: String, profileImageUrl: String?): User {
        val user = mock(User::class.java)
        lenient().`when`(user.id).thenReturn(userId)
        lenient().`when`(user.nickname).thenReturn(nickname)
        lenient().`when`(user.profileImageUrl).thenReturn(profileImageUrl)
        return user
    }

    companion object {
        private const val LOGIN_USER_ID = 1L
        private const val TARGET_USER_ID = 2L
        private const val LIST_USER_ID = 3L
    }
}
