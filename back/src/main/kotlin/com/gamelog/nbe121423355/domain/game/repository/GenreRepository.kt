package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.Genre
import org.springframework.data.jpa.repository.JpaRepository

interface GenreRepository : JpaRepository<Genre, Long> {
    fun findByIgdbGenreId(igdbGenreId: Long?): Genre?
}
