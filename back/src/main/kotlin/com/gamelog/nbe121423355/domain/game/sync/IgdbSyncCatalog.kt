package com.gamelog.nbe121423355.domain.game.sync

import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse.NamedResource
import com.gamelog.nbe121423355.domain.game.entity.*
import com.gamelog.nbe121423355.domain.game.service.NewGameRecommendationRegistration
import com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder
import com.gamelog.nbe121423355.global.interop.javaNonNull
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Service
class IgdbSyncCatalog(
    private val em: EntityManager,
    private val jdbc: NamedParameterJdbcTemplate,
    private val changeRecorder: RelatedGameChangeRecorder,
    private val recommendationRegistration: NewGameRecommendationRegistration,
) {
    @JvmRecord
    data class Counts(val inserted: Long, val updated: Long, val deleted: Long, val skipped: Long)

    @Transactional(propagation = MANDATORY)
    fun apply(page: List<IgdbGameResponse>, cutoff: Long): Counts {
        val existing = em.createQuery("select g from Game g where g.igdbId in :ids", Game::class.java)
            .setParameter("ids", page.map { it.id })
            .setLockMode(PESSIMISTIC_WRITE)
            .resultList
            .associateBy { it.igdbId }
        val previousGenres = existing.values.associate { game ->
            requireNotNull(game.id) to jdbc.queryForList(
                "SELECT genre_id FROM game_genres WHERE game_id = :id",
                mapOf("id" to game.id), Long::class.javaObjectType,
            ).map { requireNotNull(it) }
        }
        val removed = page.filterNot { eligible(it, cutoff) }.mapNotNull { existing[it.id] }
        if (removed.isNotEmpty()) {
            removed.forEach { game ->
                changeRecorder.record(requireNotNull(game.id), RelatedGameChangeReason.GAME_DELETED,
                    previousGenres[game.id].orEmpty())
            }
            purge(removed.map { requireNotNull(it.id) })
            removed.forEach { em.detach(it) }
        }
        val accepted = page.filter { eligible(it, cutoff) }
        val inserted = accepted.count { it.id !in existing }.toLong()
        if (accepted.isEmpty()) {
            return Counts(0, 0, removed.size.toLong(), (page.size - removed.size).toLong())
        }
        val genres = resources(
            accepted, IgdbGameResponse::genres, Genre::class.java, "igdbGenreId",
            id = { it.igdbGenreId }, create = Genre::createFromIgdb, rename = Genre::updateName,
        )
        val platforms = resources(
            accepted, IgdbGameResponse::platforms, Platform::class.java, "igdbPlatformsId",
            id = { it.igdbPlatformsId }, create = Platform::createFromIgdb, rename = Platform::updateName,
        )
        val series = resources(
            accepted, IgdbGameResponse::collections, GameSeries::class.java, "igdbId",
            id = { it.igdbId }, create = GameSeries::createFromIgdb, rename = GameSeries::updateName,
        )
        val newGames = mutableListOf<Game>()
        val games = accepted.map { response ->
            existing[response.id]?.apply { updateFromIgdb(response) }
                ?: Game.createFromIgdb(response).also { game ->
                    em.persist(game)
                    newGames.add(game)
                }
        }
        em.flush()
        // 기존 연결을 제거한 뒤 현재 응답으로 재구성: 제거된 장르/플랫폼/시리즈도 반영합니다.
        val params = mapOf("ids" to games.map { it.id })
        for (table in listOf("game_genres", "game_platforms", "game_series_games")) {
            jdbc.update("delete from $table where game_id in (:ids)", params)
        }
        for ((index, response) in accepted.withIndex()) {
            val game = games[index]
            for (resource in unique(response.genres)) {
                em.persist(GameGenre(game, genres.getValue(requireNotNull(resource.id))))
            }
            for (resource in unique(response.platforms)) {
                em.persist(GamePlatform(game, platforms.getValue(requireNotNull(resource.id))))
            }
            for (resource in unique(response.collections)) {
                em.persist(GameSeriesGame(series.getValue(requireNotNull(resource.id)), game))
            }
        }
        em.flush()
        games.sortedBy { it.id }.forEach { game ->
            changeRecorder.record(requireNotNull(game.id),
                if (game in newGames) RelatedGameChangeReason.GAME_ADDED else RelatedGameChangeReason.GAME_INFORMATION,
                previousGenres[game.id].orEmpty())
        }
        recommendationRegistration.register(newGames)
        return Counts(inserted, accepted.size - inserted, removed.size.toLong(),
            (page.size - accepted.size - removed.size).toLong())
    }

    private fun <T : Any> resources(
        games: List<IgdbGameResponse>,
        extract: (IgdbGameResponse) -> List<NamedResource>?,
        type: Class<T>,
        field: String,
        id: (T) -> Long?,
        create: (Long?, String?) -> T,
        rename: (T, String?) -> Unit,
    ): Map<Long, T> {
        // 기존 toMap과 같이 중복 ID는 마지막 값을 사용하고 null 이름은 거부합니다.
        val names = games.flatMap { unique(extract(it)) }
            .associateTo(hashMapOf()) { it.id to javaNonNull(it.name) }
        if (names.isEmpty()) return emptyMap()
        val result = em.createQuery("select r from ${type.simpleName} r where r.$field in :ids", type)
            .setParameter("ids", names.keys)
            .resultList
            .associateByTo(hashMapOf()) { requireNotNull(id(it)) }
        names.forEach { (key, name) ->
            val entity = result[key]
            if (entity != null) {
                rename(entity, name)
            } else {
                val created = create(key, name)
                em.persist(created)
                result[requireNotNull(key)] = created
            }
        }
        return result
    }

    // 사용자 요청 정책: 출시일이 사라지거나 범위 밖으로 바뀐 게임은 연결된 기록도 제거합니다.
    private fun purge(ids: List<Long>) {
        val params = mapOf("ids" to ids)
        val userGames = "select id from user_games where game_id in (:ids)"
        val reviews = "select id from reviews where user_game_id in ($userGames)"
        jdbc.update(
            "delete from review_comment_likes where comment_id in (select id from review_comment where review_id in ($reviews))",
            params,
        )
        jdbc.update("delete from review_comment where parent_id is not null and review_id in ($reviews)", params)
        jdbc.update("delete from review_comment where review_id in ($reviews)", params)
        jdbc.update("delete from review_likes where review_id in ($reviews)", params)
        jdbc.update("delete from review_reports where review_id in ($reviews)", params)
        jdbc.update("delete from reviews where user_game_id in ($userGames)", params)
        for (table in listOf("user_games", "user_favorite_game", "user_preference_game",
            "game_genres", "game_platforms", "game_series_games")) {
            jdbc.update("delete from $table where game_id in (:ids)", params)
        }
        jdbc.update("delete from games where id in (:ids)", params)
    }

    companion object {
        @JvmStatic
        fun eligible(game: IgdbGameResponse, cutoff: Long): Boolean =
            game.first_release_date?.let { it in 0..cutoff } == true

        private fun unique(items: List<NamedResource>?): List<NamedResource> =
            items.orEmpty().associateByTo(hashMapOf()) { it.id }.values.toList()
    }
}
