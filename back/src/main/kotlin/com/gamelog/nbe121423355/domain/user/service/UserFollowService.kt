package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.notification.entity.NotificationCancelled
import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import com.gamelog.nbe121423355.domain.notification.entity.NotificationRequested
import com.gamelog.nbe121423355.domain.notification.entity.NotificationTarget
import com.gamelog.nbe121423355.domain.user.dto.FollowPageResponseDto
import com.gamelog.nbe121423355.domain.user.dto.FollowStatusResponseDto
import com.gamelog.nbe121423355.domain.user.dto.FollowUserResponseDto
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFollow
import com.gamelog.nbe121423355.domain.user.entity.UserFollowId
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.global.interop.javaTrim
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Order
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserFollowService(
    private val events: ApplicationEventPublisher,
    private val userFollowRepository: UserFollowRepository,
    private val userRepository: UserRepository,
) {
    fun searchUsers(
        loginUserId: Long?,
        keyword: String?,
        page: Int,
        size: Int,
    ): FollowPageResponseDto {
        validatePageRequest(page, size)
        val term = keyword?.javaTrim().orEmpty()
        if (term.isEmpty() || term.length > 50) {
            throw ServiceException("400-9", "검색어는 1자 이상 50자 이하로 입력해 주세요.")
        }
        val pageable = PageRequest.of(page, size, Sort.by("nickname", "id"))
        val result =
            if (loginUserId == null)
                userRepository.findByNicknameContainingIgnoreCaseAndDeletedAtIsNull(
                    term,
                    pageable,
                )
            else
                userRepository.findByNicknameContainingIgnoreCaseAndIdNotAndDeletedAtIsNull(
                    term,
                    loginUserId,
                    pageable,
                )
        val followedIds =
            findFollowedUserIds(
                loginUserId,
                result.content.map { requireNotNull(it.id) },
            )
        val users =
            result.content.map { user ->
                FollowUserResponseDto(
                    user.id,
                    user.nickname,
                    user.profileImageUrl,
                    null,
                    followedIds.contains(user.id),
                    (user.id == loginUserId),
                )
            }
        return FollowPageResponseDto(
            users,
            result.number,
            result.size,
            result.totalElements,
            result.totalPages,
            result.hasNext(),
        )
    }

    @Transactional
    fun follow(followerId: Long, targetUserId: Long?): FollowStatusResponseDto {
        validateNotSelfFollow(followerId, targetUserId)
        val follower = getUser(followerId)
        val followee = getUser(targetUserId)
        val followId = UserFollowId(followerId, targetUserId)
        if (!userFollowRepository.existsById(followId)) {
            userFollowRepository.save(UserFollow(follower, followee))
            events.publishEvent(
                NotificationRequested(
                    recipients = setOf(requireNotNull(targetUserId)),
                    actorId = followerId,
                    kind = NotificationKind.FOLLOW,
                    targetType = NotificationTarget.USER,
                    targetId = followerId,
                    sourceKey = "follow:$followerId:$targetUserId",
                )
            )
        }
        return FollowStatusResponseDto(targetUserId, true)
    }

    @Transactional
    fun unfollow(followerId: Long, targetUserId: Long?): FollowStatusResponseDto {
        validateNotSelfFollow(followerId, targetUserId)
        getUser(targetUserId)
        val followId = UserFollowId(followerId, targetUserId)
        if (userFollowRepository.existsById(followId)) {
            userFollowRepository.deleteById(followId)
            events.publishEvent(NotificationCancelled("follow:$followerId:$targetUserId"))
        }
        return FollowStatusResponseDto(targetUserId, false)
    }

    fun getFollowing(
        loginUserId: Long?,
        targetUserId: Long?,
        page: Int,
        size: Int,
    ): FollowPageResponseDto {
        getUser(targetUserId)
        validatePageRequest(page, size)
        val pageable =
            PageRequest.of(
                page,
                size,
                Sort.by(
                    Order.desc("createdDate"),
                    Order.desc("id.followingId"),
                ),
            )
        val followPage =
            userFollowRepository.findAllByFollower_Id(targetUserId, pageable)
        return createPageResponse(
            loginUserId,
            followPage,
            { it.followee },
        )
    }

    fun getFollowers(
        loginUserId: Long?,
        targetUserId: Long?,
        page: Int,
        size: Int,
    ): FollowPageResponseDto {
        getUser(targetUserId)
        validatePageRequest(page, size)
        val pageable =
            PageRequest.of(
                page,
                size,
                Sort.by(
                    Order.desc("createdDate"),
                    Order.desc("id.followerId"),
                ),
            )
        val followPage =
            userFollowRepository.findAllByFollowee_Id(targetUserId, pageable)
        return createPageResponse(
            loginUserId,
            followPage,
            { it.follower },
        )
    }

    private fun createPageResponse(
        loginUserId: Long?,
        followPage: Page<UserFollow>,
        userExtractor: (UserFollow) -> User,
    ): FollowPageResponseDto {
        val displayedUserIds = followPage.content.map { requireNotNull(userExtractor(it).id) }
        val followedUserIds = findFollowedUserIds(loginUserId, displayedUserIds)
        val users =
            followPage.content.map { userFollow ->
                val user = userExtractor(userFollow)
                FollowUserResponseDto(
                    user.id,
                    user.nickname,
                    user.profileImageUrl,
                    userFollow.createdDate,
                    followedUserIds.contains(user.id),
                    loginUserId != null && loginUserId == user.id,
                )
            }
        return FollowPageResponseDto.from(followPage, users)
    }

    private fun findFollowedUserIds(
        loginUserId: Long?,
        displayedUserIds: List<Long>,
    ): Set<Long> {
        if (loginUserId == null || displayedUserIds.isEmpty()) {
            return emptySet()
        }
        return userFollowRepository
            .findFolloweeIdsByFollowerIdAndFolloweeIds(loginUserId, displayedUserIds)
            .toSet()
    }

    private fun validatePageRequest(page: Int, size: Int) {
        if (page < 0) {
            throw ServiceException("400-7", "페이지 번호는 0 이상이어야 합니다.")
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ServiceException("400-8", "페이지 크기는 1 이상 50 이하여야 합니다.")
        }
    }

    private fun validateNotSelfFollow(followerId: Long, targetUserId: Long?) {
        if (followerId == targetUserId) {
            throw ServiceException("400-6", "자기 자신은 팔로우할 수 없습니다.")
        }
    }

    // 탈퇴한 유저는 존재하지 않는 사용자로 취급 (팔로우하기, 팔로잉/팔로워 목록 모두 여기를 거침)
    private fun getUser(userId: Long?): User {
        return userRepository
            .findByIdAndDeletedAtIsNull(userId)
            ?: throw ServiceException("404-1", "존재하지 않는 유저입니다.")
    }

    companion object {
        private const val MAX_PAGE_SIZE = 50
    }
}
