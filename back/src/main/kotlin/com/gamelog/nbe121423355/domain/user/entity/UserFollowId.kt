package com.gamelog.nbe121423355.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class UserFollowId(
    @field:Column(name = "follower_id", nullable = false) val followerId: Long?,
    @field:Column(name = "following_id", nullable = false) val followingId: Long?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
