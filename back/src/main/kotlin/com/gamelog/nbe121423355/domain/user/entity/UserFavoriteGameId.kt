package com.gamelog.nbe121423355.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class UserFavoriteGameId(
    @field:Column(name = "user_id", nullable = false) val userId: Long?,
    @field:Column(name = "game_id", nullable = false) val gameId: Long?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
