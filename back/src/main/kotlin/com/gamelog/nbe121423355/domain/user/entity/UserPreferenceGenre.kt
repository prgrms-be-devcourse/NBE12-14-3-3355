package com.gamelog.nbe121423355.domain.user.entity

import com.gamelog.nbe121423355.domain.game.entity.Genre
import jakarta.persistence.*

@Entity
class UserPreferenceGenre {
    @EmbeddedId
    var id: UserPreferenceGenreId? = null
        protected set

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    @MapsId("genreId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    lateinit var genre: Genre
        protected set

    constructor(user: User, genre: Genre) {
        this.id = UserPreferenceGenreId(user.id, genre.id)
        this.user = user
        this.genre = genre
    }

    constructor(id: UserPreferenceGenreId?, user: User, genre: Genre) {
        this.id = id
        this.user = user
        this.genre = genre
    }

    constructor()
}
