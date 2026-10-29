package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.dto.GameSort
import com.gamelog.nbe121423355.domain.game.entity.Game
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import java.time.LocalDate
import java.util.LinkedHashMap
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.stereotype.Repository

/** 필터/정렬에 필요한 값으로 페이지를 먼저 선택하고 상세 행은 해당 페이지에만 조회합니다. */
@Repository
class GameFilteredQueryRepository(
    private val entityManager: EntityManager,
) {
    @SuppressWarnings("unchecked")
    fun findPage(
        keyword: String?,
        genreIds: List<Long>?,
        platformIds: List<Long>?,
        sort: GameSort?,
        minDate: LocalDate?,
        maxDate: LocalDate?,
        pageable: Pageable,
    ): Page<Game> {
        val parameters: MutableMap<String, Any?> = LinkedHashMap()
        parameters.put("minDate", minDate)
        parameters.put("maxDate", maxDate)
        val where: StringBuilder =
            StringBuilder(
                """
            WHERE g.release_date >= :minDate AND g.release_date < :maxDate

            """
            )
        if (keyword != null) {
            where.append(" AND LOWER(g.title) LIKE LOWER(:keyword) ESCAPE \'!\'\n")
            parameters.put("keyword", keyword)
        }
        val filterGenres = genreIds != null && !genreIds.isEmpty()
        val filterPlatforms = platformIds != null && !platformIds.isEmpty()
        if (filterGenres) {
            where.append(
                """
                 AND EXISTS (SELECT 1 FROM game_genres gg
                             WHERE gg.game_id = g.id AND gg.genre_id IN (:genreIds))

                """
            )
            parameters.put("genreIds", genreIds)
        }
        if (filterPlatforms) {
            where.append(
                """
                 AND EXISTS (SELECT 1 FROM game_platforms gp
                             WHERE gp.game_id = g.id AND gp.platform_id IN (:platformIds))

                """
            )
            parameters.put("platformIds", platformIds)
        }
        // 필터 조인에서 PK 본문 조회 대신 커버링 인덱스를 사용합니다.
        // MySQL 8.4용 인덱스: monitoring/search-filter-index.sql. H2에서는 주석입니다.
        val hint =
            if (filterGenres || filterPlatforms) "/*+ JOIN_INDEX(g idx_games_catalog_lookup) */ "
            else ""
        val order = orderPlan(sort)
        val sql =
            "SELECT detail.* FROM (SELECT " +
                hint +
                "g.id, " +
                order.value +
                " AS sort_value FROM games g " +
                order.join +
                where +
                " ORDER BY sort_value " +
                order.direction +
                ", g.id ASC" +
                " LIMIT :pageSize OFFSET :pageOffset) matched" +
                " JOIN games detail ON detail.id = matched.id" +
                " ORDER BY matched.sort_value " +
                order.direction +
                ", matched.id ASC"
        val contentQuery = entityManager.createNativeQuery(sql, Game::class.java)
        bind(contentQuery, parameters)
        contentQuery.setParameter("pageSize", pageable.getPageSize())
        contentQuery.setParameter("pageOffset", pageable.offset)
        val content = contentQuery.getResultList().map { it as Game }
        // COUNT는 같은 필터를 사용하되 정렬용 사용자 통계는 계산하지 않습니다.
        return PageableExecutionUtils.getPage(content, pageable) {
            val countQuery =
                entityManager.createNativeQuery(
                    "SELECT " + hint + "COUNT(*) FROM games g " + where
                )
            bind(countQuery, parameters)
            (countQuery.getSingleResult() as Number).toLong()
        }
    }

    private fun bind(query: Query, parameters: MutableMap<String, Any?>) {
        parameters.forEach(query::setParameter)
    }

    private fun orderPlan(sort: GameSort?): OrderPlan {
        if (sort == null) return OrderPlan("", "COALESCE(g.igdb_rating, -1.0)", "DESC")
        // SQL 조각은 enum으로만 선택합니다. 사용자 검색어/ID는 모두 바인딩합니다.
        return when (sort) {
            GameSort.LATEST -> OrderPlan("", "g.release_date", "DESC")
            GameSort.TITLE -> OrderPlan("", "g.title", "ASC")
            GameSort.RATING ->
                OrderPlan(
                    """
                LEFT JOIN (
                    SELECT ug.game_id, AVG(r.rating) AS score
                    FROM reviews r JOIN user_games ug ON ug.id = r.user_game_id
                    WHERE r.status IS NULL OR r.status = 'ACTIVE'
                    GROUP BY ug.game_id
                ) stats ON stats.game_id = g.id

                """,
                    "COALESCE(stats.score, 0.0)",
                    "DESC",
                )

            GameSort.LIBRARY ->
                OrderPlan(
                    """
                LEFT JOIN (
                    SELECT game_id, COUNT(*) AS score FROM user_games
                    WHERE is_in_library = true GROUP BY game_id
                ) stats ON stats.game_id = g.id

                """,
                    "COALESCE(stats.score, 0.0)",
                    "DESC",
                )

            GameSort.PLAY_TIME ->
                OrderPlan(
                    """
                LEFT JOIN (
                    SELECT game_id, AVG(play_time_hours) AS score FROM user_games
                    GROUP BY game_id
                ) stats ON stats.game_id = g.id

                """,
                    "COALESCE(stats.score, 0.0)",
                    "DESC",
                )
        }
    }

    private data class OrderPlan(val join: String, val value: String, val direction: String)
}
