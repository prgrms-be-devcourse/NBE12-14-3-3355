package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.*

@Entity
@Table(name = "genres")
class Genre protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // Genre
    @Column(name = "igdb_genre_id", unique = true)
    var igdbGenreId: Long? = null
        protected set

    @Column(nullable = false, unique = true, length = 50)
    var name: String? = null
        protected set

    fun updateName(name: String?) {
        this.name = name
    }

    companion object {

        fun createFromIgdb(igdbId: Long?, name: String?): Genre {
            val genre = Genre()
            genre.igdbGenreId = igdbId
            genre.name = name
            return genre
        }
    }
}
