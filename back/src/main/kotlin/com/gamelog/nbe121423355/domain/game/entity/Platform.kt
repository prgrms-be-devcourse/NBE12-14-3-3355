package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.*

@Entity
@Table(name = "platforms")
class Platform protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // Platform
    @Column(name = "igdb_platforms_id", unique = true)
    var igdbPlatformsId: Long? = null
        protected set

    // Platform의 name
    @Column(nullable = false, unique = true, length = 255)
    var name: String? = null
        protected set

    fun updateName(name: String?) {
        this.name = name
    }

    companion object {

        fun createFromIgdb(igdbId: Long?, name: String?): Platform {
            val platform = Platform()
            platform.igdbPlatformsId = igdbId
            platform.name = name
            return platform
        }
    }
}
