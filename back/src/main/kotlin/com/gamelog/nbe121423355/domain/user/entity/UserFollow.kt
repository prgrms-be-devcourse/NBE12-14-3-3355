package com.gamelog.nbe121423355.domain.user.entity

import com.gamelog.nbe121423355.global.interop.javaNonNull
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*

@Entity
class UserFollow : BaseEntity {
    @EmbeddedId
    var id: UserFollowId? = null
        protected set

    // 팔로우하는 사용자
    @MapsId("followerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    lateinit var follower: User
        protected set

    // 팔로우를 받는 대상 사용자
    @MapsId("followingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "following_id", nullable = false)
    lateinit var followee: User
        protected set

    constructor(
        follower: User,
        followee: User,
    ) {
        this.follower = javaNonNull(follower, "팔로우하는 사용자는 필수입니다.")
        this.followee = javaNonNull(followee, "팔로우 대상 사용자는 필수입니다.")
        val followerId: Long = javaNonNull(follower.id, "팔로우하는 사용자 ID는 필수입니다.")
        val followeeId: Long = javaNonNull(followee.id, "팔로우 대상 사용자 ID는 필수입니다.")
        require(followerId != followeeId) { "자기 자신은 팔로우할 수 없습니다." }
        this.id = UserFollowId(followerId, followeeId)
    }

    protected constructor()
}
