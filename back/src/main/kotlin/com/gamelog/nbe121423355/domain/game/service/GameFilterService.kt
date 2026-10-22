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
                    .stream()
                    .map({ genre -> GameFilterResponse.Option(genre.id, genre.name) })
                    .toList()
            val platforms: MutableList<GameFilterResponse.Option>? =
                platformRepository
                    .findAll(Sort.by("name"))
                    .stream()
                    .map({ platform -> GameFilterResponse.Option(platform.id, platform.name) })
                    .toList()
            return GameFilterResponse(genres, platforms)
        }
}
