package com.gamelog.nbe121423355.domain.game.entity

import com.gamelog.nbe121423355.global.interop.javaNonNull
import jakarta.persistence.*

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "game_platforms")
class GamePlatform {
    @EmbeddedId
    var id: GamePlatformId? = null
        protected set

    @MapsId("gameId")
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    lateinit var game: Game
        protected set

    @MapsId("platformId")
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_id", nullable = false)
    lateinit var platform: com.gamelog.nbe121423355.domain.game.entity.Platform
        protected set

    // ID가 부여된 부모 엔티티를 연결한다.
    constructor(game: Game, platform: com.gamelog.nbe121423355.domain.game.entity.Platform) {
        this.game = javaNonNull(game, "game")
        this.platform = javaNonNull(platform, "platform")
        this.id = GamePlatformId(game.id, platform.id)
    }

    protected constructor()
}
