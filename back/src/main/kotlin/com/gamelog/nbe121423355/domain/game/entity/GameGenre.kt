package com.gamelog.nbe121423355.domain.game.entity

import com.gamelog.nbe121423355.global.interop.javaNonNull
import jakarta.persistence.*

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "game_genres")
class GameGenre {
    @EmbeddedId
    var id: GameGenreId? = null
        protected set

    @MapsId("gameId")
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    lateinit var game: Game
        protected set

    @MapsId("genreId")
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    lateinit var genre: Genre
        protected set

    // ID가 부여된 부모 엔티티를 연결한다.
    constructor(game: Game, genre: Genre) {
        this.game = javaNonNull(game, "game")
        this.genre = javaNonNull(genre, "genre")
        this.id = GameGenreId(game.id, genre.id)
    }

    protected constructor()
}
