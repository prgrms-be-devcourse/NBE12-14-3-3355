package com.gamelog.nbe121423355.domain.game.entity

import com.gamelog.nbe121423355.global.interop.javaNonNull
import jakarta.persistence.*

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "game_series_games")
class GameSeriesGame {
    @EmbeddedId
    var id: GameSeriesGameId? = null
        protected set

    @MapsId("seriesId")
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    lateinit var series: GameSeries
        protected set

    @MapsId("gameId")
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    lateinit var game: Game
        protected set

    // ID가 부여된 부모 엔티티를 연결한다.
    constructor(series: GameSeries, game: Game) {
        this.series = javaNonNull(series, "series")
        this.game = javaNonNull(game, "game")
        this.id = GameSeriesGameId(series.id, game.id)
    }

    protected constructor()
}
