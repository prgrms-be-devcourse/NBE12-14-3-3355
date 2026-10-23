package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameInputChangeRepository
import java.time.LocalDateTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(propagation = MANDATORY)
class RelatedGameChangeRecorder(
    private val changes: RelatedGameInputChangeRepository,
    private val jdbc: JdbcTemplate,
) {
    @JvmOverloads
    fun record(gameId: Long, reason: RelatedGameChangeReason, previousGenres: Collection<Long> = emptyList()) {
        val currentGenres = jdbc.queryForList(
            "SELECT genre_id FROM game_genres WHERE game_id = ? ORDER BY genre_id",
            Long::class.javaObjectType, gameId,
        )
        changes.record(gameId, previousGenres + currentGenres, setOf(reason), LocalDateTime.now())
    }

    fun recordWithdrawnReviews(userId: Long) {
        jdbc.queryForList(
            """
            SELECT DISTINCT ug.game_id FROM user_games ug JOIN reviews r ON r.user_game_id = ug.id
            WHERE ug.user_id = ? AND (r.status IS NULL OR r.status = 'ACTIVE') ORDER BY ug.game_id
            """.trimIndent(), Long::class.javaObjectType, userId,
        ).forEach { record(it, RelatedGameChangeReason.REVIEW) }
    }
}
