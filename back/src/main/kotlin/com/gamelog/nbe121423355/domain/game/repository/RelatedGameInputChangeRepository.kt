package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameInputChange
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Repository
@Transactional(readOnly = true)
class RelatedGameInputChangeRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    // 원본 저장과 같은 트랜잭션에서만 기록한다. 신규 행 경합도 upsert의 행 잠금으로 직렬화한다.
    @Transactional(propagation = MANDATORY)
    fun record(
        gameId: Long,
        affectedGenreIds: Collection<Long>,
        reasons: Set<RelatedGameChangeReason>,
        changedAt: LocalDateTime,
    ): Long {
        require(gameId > 0) { "게임 ID는 양수여야 합니다." }
        require(affectedGenreIds.all { it > 0 }) { "장르 ID는 양수여야 합니다." }
        val reasonMask = RelatedGameChangeReason.maskOf(reasons)
        jdbcTemplate.update(
            """
            INSERT INTO related_game_input_changes
                (game_id, change_version, expanded_version, affected_genre_ids, reason_mask, changed_at)
            VALUES (?, 0, 0, JSON_ARRAY(), 0, ?)
            ON DUPLICATE KEY UPDATE game_id = game_id
            """.trimIndent(),
            gameId, changedAt,
        )
        val current = requireNotNull(find(gameId, forUpdate = true))
        val nextVersion = Math.addExact(current.changeVersion, 1L)
        val genreIds = (current.affectedGenreIds + affectedGenreIds).distinct().sorted()
        jdbcTemplate.update(
            """
            UPDATE related_game_input_changes
            SET change_version = ?, affected_genre_ids = CAST(? AS JSON),
                reason_mask = ?, changed_at = GREATEST(changed_at, ?)
            WHERE game_id = ?
            """.trimIndent(),
            nextVersion, objectMapper.writeValueAsString(genreIds),
            current.reasonMask or reasonMask, changedAt, gameId,
        )
        return nextVersion
    }

    // 영향 대상 저장과 같은 트랜잭션에서 캡처 버전까지만 확인한다. 새 변경의 장르·이유는 남긴다.
    @Transactional(propagation = MANDATORY)
    fun markExpanded(gameId: Long, capturedVersion: Long): Boolean {
        require(gameId > 0 && capturedVersion > 0)
        val current = checkNotNull(find(gameId, forUpdate = true)) { "변경 원천이 없습니다." }
        require(capturedVersion <= current.changeVersion) { "기록된 변경 버전을 넘을 수 없습니다." }
        if (capturedVersion <= current.expandedVersion) return false
        jdbcTemplate.update(
            """
            UPDATE related_game_input_changes
            SET expanded_version = ?,
                affected_genre_ids = CASE WHEN change_version = ? THEN JSON_ARRAY() ELSE affected_genre_ids END,
                reason_mask = CASE WHEN change_version = ? THEN 0 ELSE reason_mask END
            WHERE game_id = ?
            """.trimIndent(),
            capturedVersion, capturedVersion, capturedVersion, gameId,
        )
        return true
    }

    fun findByGameId(gameId: Long): RelatedGameInputChange? = find(gameId, forUpdate = false)

    // JDBC로 읽어 같은 영속성 컨텍스트의 오래된 엔티티 대신 최신 잠금 행을 사용한다.
    private fun find(gameId: Long, forUpdate: Boolean): RelatedGameInputChange? =
        jdbcTemplate.query(
            """
            SELECT game_id, change_version, expanded_version, affected_genre_ids, reason_mask, changed_at
            FROM related_game_input_changes WHERE game_id = ?
            """.trimIndent() + if (forUpdate) " FOR UPDATE" else "",
            RowMapper { row, _ ->
                RelatedGameInputChange(
                    row.getLong("game_id"), row.getLong("change_version"), row.getLong("expanded_version"),
                    objectMapper.readValue(row.getString("affected_genre_ids"), LongArray::class.java).toList(),
                    row.getLong("reason_mask"), row.getObject("changed_at", LocalDateTime::class.java),
                )
            },
            gameId,
        ).singleOrNull()
}
