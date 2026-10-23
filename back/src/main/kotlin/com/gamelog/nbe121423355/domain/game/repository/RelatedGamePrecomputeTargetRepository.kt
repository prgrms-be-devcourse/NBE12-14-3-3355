package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeTargetType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class RelatedGamePrecomputeTargetRepository(private val jdbcTemplate: JdbcTemplate) {
    fun count(type: RelatedGamePrecomputeTargetType, upperId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM games game WHERE game.id <= ? AND ${condition(type)}",
            Long::class.javaObjectType,
            upperId,
        ) ?: 0L

    fun findIds(
        type: RelatedGamePrecomputeTargetType,
        afterId: Long,
        upperId: Long,
        limit: Int,
    ): MutableList<Long> {
        require(limit > 0)
        return jdbcTemplate.query(
            """
            SELECT game.id FROM games game
            WHERE game.id > ? AND game.id <= ? AND ${condition(type)}
            ORDER BY game.id
            LIMIT ?
            """.trimIndent(),
            RowMapper<Long> { row, _ -> row.getLong(1) },
            afterId,
            upperId,
            limit,
        )
    }

    // SQL 조건은 내부 enum으로만 선택하고 요청 문자열은 조합하지 않는다.
    private fun condition(type: RelatedGamePrecomputeTargetType): String = when (type) {
        RelatedGamePrecomputeTargetType.FAILED -> """
            EXISTS (
                SELECT 1 FROM related_game_calculations calculation
                WHERE calculation.game_id = game.id
                  AND calculation.last_attempt_status = 'FAILED'
            )
        """.trimIndent()
        // Game에서 출발하여 계산 상태 행 자체가 없는 게임도 복구 대상으로 포함한다.
        RelatedGamePrecomputeTargetType.UNREADY -> """
            NOT EXISTS (
                SELECT 1 FROM related_game_calculations calculation
                WHERE calculation.game_id = game.id
                  AND calculation.last_success_at IS NOT NULL
            )
        """.trimIndent()
        else -> error("실패·미완료 대상 조회에만 사용합니다.")
    }
}
