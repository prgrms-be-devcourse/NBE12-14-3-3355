package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.dto.GameFilterResponse
import com.gamelog.nbe121423355.domain.game.dto.GameFilterResponse.Option
import com.gamelog.nbe121423355.domain.game.repository.GenreRepository
import com.gamelog.nbe121423355.domain.game.repository.PlatformRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameFilterService
constructor(
    private val genreRepository: GenreRepository,
    private val platformRepository: PlatformRepository,
) {
    @get:Transactional(readOnly = true)
    val filters: GameFilterResponse
        get() {
            val genres =
                genreRepository
                    .findAll(Sort.by("name"))
                    .map { genre -> Option(genre.id, genre.name) }
            val platforms =
                platformRepository
                    .findAll(Sort.by("name"))
                    .map { platform -> Option(platform.id, platform.name) }
            return GameFilterResponse(genres, platforms)
        }
}
