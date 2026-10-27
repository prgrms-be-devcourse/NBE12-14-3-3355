package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode.REFRESH
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeSnapshot
import com.gamelog.nbe121423355.global.interop.formatted
import java.math.BigDecimal
import java.sql.ResultSet
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCreator
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Isolation.REPEATABLE_READ
import org.springframework.transaction.annotation.Transactional

@Repository
class RelatedGamePrecomputeSnapshotRepository(private val jdbcTemplate: JdbcTemplate) {
    // 같은 읽기 스냅샷에서 장르·후보 자격·행동 기준 게임을 준비하고 트랜잭션을 종료한다.
    @Transactional(readOnly = true, isolation = REPEATABLE_READ)
    fun load(
        mode: RelatedGamePrecomputeMode,
        upperId: Long,
        limit: Int,
    ): RelatedGamePrecomputeSnapshot {
        val genres = mutableMapOf<Long, MutableList<Long>>()
        read(
            """
            SELECT g.id, gg.genre_id
            FROM games g
            LEFT JOIN game_genres gg ON gg.game_id = g.id
            WHERE g.id <= ?
            ORDER BY g.id, gg.genre_id

            """,
            upperId,
            RowCallbackHandler { row: ResultSet ->
                val ids = genres.getOrPut(row.getLong(1)) { mutableListOf() }
                val genre = row.getLong(2)
                if (!row.wasNull()) {
                    ids.add(genre)
                }
            },
        )
        val readyCondition =
            if (mode == REFRESH)
                """
            AND EXISTS (
                SELECT 1 FROM related_game_calculations calculation
                WHERE calculation.game_id = g.id AND calculation.last_success_at IS NOT NULL
            )

            """
            else ""
        val ratings = mutableMapOf<Long, BigDecimal>()
        read(
            """
            SELECT g.id, g.igdb_rating
            FROM games g
            LEFT JOIN (
                SELECT ug.game_id, COUNT(r.rating) AS rating_count, AVG(r.rating) AS avg_rating
                FROM user_games ug
                JOIN reviews r ON r.user_game_id = ug.id
                    AND (r.status IS NULL OR r.status = 'ACTIVE')
                WHERE r.rating IS NOT NULL
                GROUP BY ug.game_id
            ) stats ON stats.game_id = g.id
            WHERE g.id <= ? AND g.igdb_rating >= 70.0
              AND (COALESCE(stats.rating_count, 0) < 5 OR stats.avg_rating >= 3.5)
            %s

            """
                .formatted(readyCondition),
            upperId,
            RowCallbackHandler { row: ResultSet ->
                ratings[row.getLong(1)] = requireNotNull(row.getBigDecimal(2)) { "추천 후보의 IGDB 평점이 없습니다." }
            },
        )
        val interactionSources = mutableSetOf<Long>()
        read(
            """
            SELECT DISTINCT ug.game_id
            FROM user_games ug
            LEFT JOIN reviews r ON r.user_game_id = ug.id
                AND (r.status IS NULL OR r.status = 'ACTIVE')
            WHERE ug.game_id <= ?
              AND (ug.play_status IS NOT NULL OR ug.is_liked = TRUE OR r.rating >= 4.0)

            """,
            upperId,
            RowCallbackHandler { row: ResultSet -> interactionSources.add(row.getLong(1)) },
        )
        return RelatedGamePrecomputeSnapshot(
            mode = mode,
            limit = limit,
            sourceGenres = genres,
            eligibleRatings = ratings,
            interactionSources = interactionSources,
        )
    }

    private fun read(sql: String, upperId: Long, handler: RowCallbackHandler) {
        // MySQL 결과를 순차 소비하여 전체 행 배열과 JPA 엔티티를 중복 적재하지 않는다.
        jdbcTemplate.query(
            PreparedStatementCreator { connection: java.sql.Connection ->
                val statement =
                    connection.prepareStatement(
                        sql,
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY,
                    )
                statement.fetchSize = Int.MIN_VALUE
                statement.setLong(1, upperId)
                statement
            },
            handler,
        )
    }
}
