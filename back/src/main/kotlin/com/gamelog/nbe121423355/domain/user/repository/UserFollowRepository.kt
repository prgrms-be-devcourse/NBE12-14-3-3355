package com.gamelog.nbe121423355.domain.user.repository

import com.gamelog.nbe121423355.domain.user.entity.UserFollow
import com.gamelog.nbe121423355.domain.user.entity.UserFollowId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserFollowRepository : JpaRepository<UserFollow, UserFollowId> {
    @EntityGraph(attributePaths = ["followee"])
    fun findAllByFollower_Id(
        followerId: Long?,
        pageable: Pageable?,
    ): Page<UserFollow>

    @EntityGraph(attributePaths = ["follower"])
    fun findAllByFollowee_Id(
        followeeId: Long?,
        pageable: Pageable?,
    ): Page<UserFollow>

    @Query(
        """
            SELECT userFollow.followee.id
            FROM UserFollow userFollow
            WHERE userFollow.follower.id = :followerId
              AND userFollow.followee.id IN :followeeIds

            """
    )
    fun findFolloweeIdsByFollowerIdAndFolloweeIds(
        @Param("followerId") followerId: Long?,
        @Param("followeeIds") followeeIds: Collection<Long>?,
    ): MutableList<Long>

    fun existsByFollowerIdAndFolloweeId(
        followerId: Long?,
        followingId: Long?,
    ): Boolean

    // 회원탈퇴: 팔로우·팔로워 양방향 삭제
    @Modifying
    @Query(
        """
            DELETE FROM UserFollow userFollow
            WHERE userFollow.follower.id = :userId
               OR userFollow.followee.id = :userId

            """
    )
    fun deleteAllByUserId(@Param("userId") userId: Long?)
}
