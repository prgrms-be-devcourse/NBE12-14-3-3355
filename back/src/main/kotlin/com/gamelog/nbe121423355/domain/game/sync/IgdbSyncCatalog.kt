package com.gamelog.nbe121423355.domain.game.sync

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse.NamedResource
import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.game.service.NewGameRecommendationRegistration
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
import java.util.*
import java.util.ArrayList
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Function
import java.util.stream.Collectors
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Service
class IgdbSyncCatalog
constructor(
    private val em: EntityManager,
    private val jdbc: NamedParameterJdbcTemplate,
    private val recommendationRegistration: NewGameRecommendationRegistration,
) {

    @JvmRecord
    data class Counts(
        val inserted: Long,
        val updated: Long,
        val deleted: Long,
        val skipped: Long,
    ) {}

    @Transactional(propagation = MANDATORY)
    fun apply(page: MutableList<IgdbGameResponse>, cutoff: Long): Counts {
        val existing =
            em.createQuery("select g from Game g where g.igdbId in :ids", Game::class.java)
                .setParameter("ids", page.stream().map(IgdbGameResponse::id).toList())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList()
                .stream()
                .collect(Collectors.toMap({ obj: Game -> obj.igdbId }, Function.identity()))
        val removed = page.filter { !eligible(it, cutoff) }.mapNotNull { existing[it.id] }
        if (!removed.isEmpty()) {
            purge(removed.map { requireNotNull(it.id) }.toMutableList())
            removed.forEach { em.detach(it) }
        }
        val accepted = page.stream().filter({ g -> IgdbSyncCatalog.eligible(g, cutoff) }).toList()
        val inserted = accepted.stream().filter({ g -> !existing.containsKey(g.id) }).count()
        if (accepted.isEmpty())
            return Counts(0, 0, removed.size.toLong(), (page.size - removed.size).toLong())
        val genres =
            resources(
                accepted,
                IgdbGameResponse::genres,
                Genre::class.java,
                "igdbGenreId",
                Function { obj: Genre -> obj.igdbGenreId },
                BiFunction { igdbId: Long?, name: String? -> Genre.createFromIgdb(igdbId, name) },
                BiConsumer { obj: Genre, name: String? -> obj.updateName(name) },
            )
        val platforms =
            resources(
                accepted,
                IgdbGameResponse::platforms,
                com.gamelog.nbe121423355.domain.game.entity.Platform::class.java,
                "igdbPlatformsId",
                Function { obj: com.gamelog.nbe121423355.domain.game.entity.Platform ->
                    obj.igdbPlatformsId
                },
                BiFunction { igdbId: Long?, name: String? ->
                    com.gamelog.nbe121423355.domain.game.entity.Platform.createFromIgdb(
                        igdbId,
                        name,
                    )
                },
                BiConsumer {
                    obj: com.gamelog.nbe121423355.domain.game.entity.Platform,
                    name: String? ->
                    obj.updateName(name)
                },
            )
        val series =
            resources(
                accepted,
                IgdbGameResponse::collections,
                GameSeries::class.java,
                "igdbId",
                Function { obj: GameSeries -> obj.igdbId },
                BiFunction { igdbId: Long?, name: String? ->
                    GameSeries.createFromIgdb(igdbId, name)
                },
                BiConsumer { obj: GameSeries, name: String? -> obj.updateName(name) },
            )
        val games = ArrayList<Game>()
        val newGames = ArrayList<Game>()
        for (response in accepted) {
            var game = existing.get(response.id)
            if (game == null) {
                game = Game.createFromIgdb(response)
                em.persist(game)
                newGames.add(game)
            } else game.updateFromIgdb(response)
            games.add(game)
        }
        em.flush()
        // 기존 연결을 제거한 뒤 현재 응답으로 재구성: 제거된 장르/플랫폼/시리즈도 반영합니다.
        val params = java.util.Map.of("ids", games.stream().map({ obj: Game -> obj.id }).toList())
        for (table in
            java.util.List.of(
                "game_genres",
                "game_platforms",
                "game_series_games",
            )) jdbc.update("delete from " + table + " where game_id in (:ids)", params)
        for (i in 0..<accepted.size) {
            val response = accepted.get(i)
            val game = games.get(i)
            for (resource in IgdbSyncCatalog.unique(response.genres)) em.persist(
                GameGenre(
                    game,
                    genres.getValue(requireNotNull(resource.id)),
                )
            )
            for (resource in IgdbSyncCatalog.unique(response.platforms)) em.persist(
                GamePlatform(
                    game,
                    platforms.getValue(requireNotNull(resource.id)),
                )
            )
            for (resource in IgdbSyncCatalog.unique(response.collections)) em.persist(
                GameSeriesGame(
                    series.getValue(requireNotNull(resource.id)),
                    game,
                )
            )
        }
        recommendationRegistration.register(newGames)
        return Counts(
            inserted,
            accepted.size - inserted,
            removed.size.toLong(),
            (page.size - accepted.size - removed.size).toLong(),
        )
    }

    private fun <T : Any> resources(
        games: MutableList<IgdbGameResponse>,
        extract: Function<IgdbGameResponse, MutableList<NamedResource>?>,
        type: Class<T>,
        field: String?,
        id: Function<T, Long?>,
        create: BiFunction<Long?, String?, T>,
        rename: BiConsumer<T, String?>,
    ): MutableMap<Long, T> {
        val names =
            games
                .stream()
                .flatMap({ g -> IgdbSyncCatalog.unique(extract.apply(g)).stream() })
                .collect(Collectors.toMap(NamedResource::id, NamedResource::name, { a, b -> b }))
        if (names.isEmpty()) return java.util.Map.of()
        val result =
            em.createQuery(
                    "select r from " + type.getSimpleName() + " r where r." + field + " in :ids",
                    type,
                )
                .setParameter("ids", names.keys)
                .getResultList()
                .stream()
                .collect(
                    Collectors.toMap(
                        { entity -> requireNotNull(id.apply(entity)) },
                        Function.identity(),
                    )
                )
        names.forEach({ key, name ->
            if (result.containsKey(key)) rename.accept(result.getValue(key), name)
            else {
                val entity = create.apply(key, name)
                em.persist(entity)
                result.put(key, entity)
            }
        })
        return result
    }

    // 사용자 요청 정책: 출시일이 사라지거나 범위 밖으로 바뀐 게임은 연결된 기록도 제거합니다.
    private fun purge(ids: MutableList<Long>?) {
        val params = java.util.Map.of("ids", ids)
        val userGames = "select id from user_games where game_id in (:ids)"
        val reviews = "select id from reviews where user_game_id in (" + userGames + ")"
        jdbc.update(
            "delete from review_comment_likes where comment_id in (select id from review_comment where review_id in (" +
                reviews +
                "))",
            params,
        )
        jdbc.update(
            "delete from review_comment where parent_id is not null and review_id in (" +
                reviews +
                ")",
            params,
        )
        jdbc.update("delete from review_comment where review_id in (" + reviews + ")", params)
        jdbc.update("delete from review_likes where review_id in (" + reviews + ")", params)
        jdbc.update("delete from review_reports where review_id in (" + reviews + ")", params)
        jdbc.update("delete from reviews where user_game_id in (" + userGames + ")", params)
        for (table in
            java.util.List.of(
                "user_games",
                "user_favorite_game",
                "user_preference_game",
                "game_genres",
                "game_platforms",
                "game_series_games",
            )) jdbc.update("delete from " + table + " where game_id in (:ids)", params)
        jdbc.update("delete from games where id in (:ids)", params)
    }

    companion object {
        @JvmStatic
        fun eligible(game: IgdbGameResponse, cutoff: Long): Boolean {
            return game.first_release_date != null &&
                game.first_release_date >= 0 &&
                game.first_release_date <= cutoff
        }

        @JvmStatic
        private fun unique(items: MutableList<NamedResource>?): MutableList<NamedResource> {
            if (items == null) return java.util.List.of()
            return ArrayList(
                items
                    .stream()
                    .collect(
                        Collectors.toMap(NamedResource::id, Function.identity(), { a, b -> b })
                    )
                    .values
            )
        }
    }
}
