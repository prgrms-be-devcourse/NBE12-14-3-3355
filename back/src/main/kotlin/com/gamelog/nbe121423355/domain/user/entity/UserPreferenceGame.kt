package com.gamelog.nbe121423355.domain.user.entity

import com.gamelog.nbe121423355.domain.game.entity.Game
import jakarta.persistence.*

@Entity
class UserPreferenceGame {
    @EmbeddedId
    var id: UserPreferenceGameId? = null
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

    constructor(user: User, game: Game) {
        this.id = UserPreferenceGameId(user.id, game.id)
        this.user = user
        this.game = game
    }

    constructor(id: UserPreferenceGameId?, user: User, game: Game) {
        this.id = id
        this.user = user
        this.game = game
    }

    constructor()
}
