package com.gamelog.nbe121423355.domain.game.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@JvmRecord
data class RelatedGamePreviewTarget(val gameId: String, val title: String)

/** 실행 준비와 같은 영향 범위를 조회하되 원천·대기 버전을 변경하지 않는다. */
@Repository
class RelatedGameChangePreviewRepository(private val jdbc: JdbcTemplate) {
    fun count(): Long = requireNotNull(jdbc.queryForObject("$PREVIEW SELECT COUNT(*) FROM preview_ids", Long::class.javaObjectType))

    fun findPage(offset: Long, size: Int): List<RelatedGamePreviewTarget> = jdbc.query(
        "$PREVIEW SELECT g.id, g.title FROM preview_ids p JOIN games g ON g.id = p.game_id ORDER BY g.id LIMIT ? OFFSET ?",
        { row, _ ->
            RelatedGamePreviewTarget(
                gameId = row.getLong("id").toString(),
                title = requireNotNull(row.getString("title")) { "조회한 게임의 제목이 없습니다." },
            )
        }, size, offset,
    )

    companion object {
        private val PREVIEW = """
            WITH pending_changes AS (
                SELECT game_id, affected_genre_ids FROM related_game_input_changes WHERE change_version > expanded_version
            ), changed_genres AS (
                SELECT j.genre_id FROM related_game_input_changes c
                JOIN JSON_TABLE(c.affected_genre_ids, '$[*]' COLUMNS(genre_id BIGINT PATH '$')) j ON TRUE
                WHERE c.change_version > c.expanded_version
                UNION
                SELECT gg.genre_id FROM game_genres gg JOIN pending_changes c ON c.game_id = gg.game_id
            ), preview_ids AS (
                SELECT t.game_id FROM related_game_refresh_targets t JOIN games g ON g.id = t.game_id
                WHERE t.required_version > t.applied_version
                UNION
                SELECT g.id FROM games g JOIN pending_changes c ON c.game_id = g.id
                UNION
                SELECT gg.game_id FROM game_genres gg JOIN changed_genres cg ON cg.genre_id = gg.genre_id
            )
        """.trimIndent()
    }
}
