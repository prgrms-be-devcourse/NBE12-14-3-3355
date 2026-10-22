package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.*

@Entity
@Table(name = "game_series")
class GameSeries protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "igdb_id", unique = true)
    var igdbId: Long? = null
        protected set

    @Column(nullable = false, length = 255)
    var name: String? = null
        protected set

    fun updateName(name: String?) {
        this.name = name
    }

    companion object {
        @JvmStatic
        fun createFromIgdb(igdbId: Long?, name: String?): GameSeries {
            val series = GameSeries()
            series.igdbId = igdbId
            series.name = name
            return series
        }
    }
}
