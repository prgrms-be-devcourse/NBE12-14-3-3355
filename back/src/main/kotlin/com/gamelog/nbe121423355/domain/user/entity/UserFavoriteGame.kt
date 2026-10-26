package com.gamelog.nbe121423355.domain.user.entity

import com.gamelog.nbe121423355.domain.game.entity.Game
import jakarta.persistence.*
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

@Entity
class UserFavoriteGame {
    @EmbeddedId
    var id: UserFavoriteGameId? = null
        protected set

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    @MapsId("gameId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    lateinit var game: Game
        protected set

    @field:Min(1)
    @field:Max(5)
    @Column(nullable = false)
    var displayOrder: Int? = null
        protected set

    constructor(user: User, game: Game, displayOrder: Int?) {
        validateDisplayOrder(displayOrder)
        this.id = UserFavoriteGameId(user.id, game.id)
        this.user = user
        this.game = game
        this.displayOrder = displayOrder
    }

    fun changeDisplayOrder(displayOrder: Int?) {
        validateDisplayOrder(displayOrder)
        this.displayOrder = displayOrder
    }

    fun validateDisplayOrder(displayOrder: Int?) {
        require(displayOrder != null && displayOrder in 1..5) {
            "인생게임 순서는 1~5만 가능합니다."
        }
    }

    constructor()
}
