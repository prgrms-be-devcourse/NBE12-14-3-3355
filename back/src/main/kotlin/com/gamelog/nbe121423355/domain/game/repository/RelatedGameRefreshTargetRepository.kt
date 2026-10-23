package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameRefreshTarget
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class RelatedGameRefreshTargetRepository(private val jdbcTemplate: JdbcTemplate) {
    // 공통 계산 잠금을 보유한 준비 단계에서만 호출한다. 삭제된 게임은 다시 등록하지 않는다.
    @Transactional(propagation = MANDATORY)
    fun request(
        gameId: Long,
        reasons: Set<RelatedGameChangeReason>,
        changedAt: LocalDateTime,
    ): Long? {
        require(gameId > 0) { "게임 ID는 양수여야 합니다." }
        val reasonMask = RelatedGameChangeReason.maskOf(reasons)
        val updated = jdbcTemplate.update(
            """
            INSERT INTO related_game_refresh_targets
                (game_id, required_version, applied_version, reason_mask, changed_at)
            SELECT id, 1, 0, ?, ? FROM games WHERE id = ?
            ON DUPLICATE KEY UPDATE
                required_version = required_version + 1,
                reason_mask = reason_mask | ?, changed_at = GREATEST(changed_at, ?)
            """.trimIndent(),
            reasonMask, changedAt, gameId, reasonMask, changedAt,
        )
        return if (updated == 0) null else requireNotNull(findByGameId(gameId)).requiredVersion
    }

    // 후보 교체 트랜잭션에서 사용한다. 실패/중단은 이 메서드를 호출하지 않아 대기를 유지한다.
    @Transactional(propagation = MANDATORY)
    fun markApplied(gameId: Long, capturedVersion: Long): Boolean {
        require(gameId > 0 && capturedVersion > 0)
        val current = find(gameId, forUpdate = true) ?: return false
        require(capturedVersion <= current.requiredVersion) { "갱신 필요 버전을 넘을 수 없습니다." }
        if (capturedVersion <= current.appliedVersion) return false
        jdbcTemplate.update(
            """
            UPDATE related_game_refresh_targets
            SET applied_version = ?, reason_mask = CASE WHEN required_version = ? THEN 0 ELSE reason_mask END
            WHERE game_id = ?
            """.trimIndent(),
            capturedVersion, capturedVersion, gameId,
        )
        return true
    }

    fun findByGameId(gameId: Long): RelatedGameRefreshTarget? = find(gameId, forUpdate = false)

    private fun find(gameId: Long, forUpdate: Boolean): RelatedGameRefreshTarget? =
        jdbcTemplate.query(
            """
            SELECT game_id, required_version, applied_version, reason_mask, changed_at
            FROM related_game_refresh_targets WHERE game_id = ?
            """.trimIndent() + if (forUpdate) " FOR UPDATE" else "",
            RowMapper { row, _ ->
                RelatedGameRefreshTarget(
                    row.getLong("game_id"), row.getLong("required_version"), row.getLong("applied_version"),
                    row.getLong("reason_mask"), row.getObject("changed_at", LocalDateTime::class.java),
                )
            },
            gameId,
        ).singleOrNull()
}
