package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import java.util.Collections
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Repository
class RelatedGameCandidateBatchRepository constructor(private val jdbcTemplate: JdbcTemplate) {
    // 호출자의 후보 교체 트랜잭션에 참여하며, 게임 하나의 후보를 한 SQL로 저장한다.
    @Transactional(propagation = MANDATORY)
    fun insertAll(sourceGameId: Long, candidates: MutableList<RelatedGameCandidateProjection>) {
        if (candidates.isEmpty()) {
            return
        }
        val values =
            java.lang.String.join(", ", Collections.nCopies(candidates.size, "(?, ?, ?, ?)"))
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
                for (index in 0..<candidates.size) {
                    val candidate = candidates.get(index)
                    val offset = index * 4
                    statement.setLong(offset + 1, sourceGameId)
                    statement.setLong(offset + 2, requireNotNull(candidate.gameId))
                    statement.setBigDecimal(offset + 3, candidate.relatedScore)
                    statement.setInt(offset + 4, index + 1)
                }
            },
        )
    }
}
