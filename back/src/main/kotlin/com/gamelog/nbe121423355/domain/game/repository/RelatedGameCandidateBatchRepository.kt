package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Repository
class RelatedGameCandidateBatchRepository(private val jdbcTemplate: JdbcTemplate) {
    // 호출자의 후보 교체 트랜잭션에 참여하며, 게임 하나의 후보를 한 SQL로 저장한다.
    @Transactional(propagation = MANDATORY)
    fun insertAll(sourceGameId: Long, candidates: List<RelatedGameCandidateProjection>) {
        if (candidates.isEmpty()) {
            return
        }
        val values = candidates.joinToString(", ") { "(?, ?, ?, ?)" }
        val sql =
            """
            INSERT INTO related_game_candidates
                (source_game_id, candidate_game_id, related_score, related_rank)
            VALUES

            """ +
                values
        jdbcTemplate.update(
            sql,
            PreparedStatementSetter { statement: java.sql.PreparedStatement ->
                candidates.forEachIndexed { index, candidate ->
                    val offset = index * 4
                    statement.setLong(offset + 1, sourceGameId)
                    statement.setLong(offset + 2, candidate.gameId)
                    statement.setBigDecimal(offset + 3, candidate.relatedScore)
                    statement.setInt(offset + 4, index + 1)
                }
            },
        )
    }
}
