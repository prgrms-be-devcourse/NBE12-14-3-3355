package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.dto.RelatedGameResponse
import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import com.gamelog.nbe121423355.domain.game.service.GamePublicationPolicy
import com.gamelog.nbe121423355.global.interop.formatted
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class RelatedGameQueryRepository(
    private val entityManager: EntityManager,
    private val publicationPolicy: GamePublicationPolicy,
) {
    // 기준 게임과 연관된 추천 후보 게임 목록을 Native SQL로 조회하여 DTO로 반환
    fun findRelatedGames(gameId: Long): List<RelatedGameResponse> {
        val games = findRows(gameId, 5, publicationPolicy.isReadyOnly).map { row ->
            val columns = row as Array<*>
            RelatedGameResponse(
                id = (columns[0] as Number).toLong(),
                title = requireNotNull(columns[1] as String?) { "조회한 게임의 제목이 없습니다." },
                coverImageUrl = columns[2] as String?,
                igdbRating = toBigDecimal(columns[3]),
                recommendationScore = toBigDecimal(columns[4]),
            )
        }
        return java.util.List.copyOf(games)
    }

    // 최초 전체 계산은 모든 게임을, 일반 갱신은 준비 완료된 게임만 후보로 사용한다.
    @Transactional(readOnly = true)
    fun findCandidatesForPrecompute(
        gameId: Long,
        limit: Int,
        readyCandidatesOnly: Boolean,
    ): List<RelatedGameCandidateProjection> {
        require(limit >= 1) { "후보 조회 개수는 1 이상이어야 합니다." }
        val candidates = findRows(gameId, limit, readyCandidatesOnly).map { row ->
            val columns = row as Array<*>
            RelatedGameCandidateProjection(
                gameId = (columns[0] as Number).toLong(),
                relatedScore = toBigDecimal(columns[4]),
            )
        }
        return java.util.List.copyOf(candidates)
    }

    private fun findRows(gameId: Long, limit: Int, readyCandidatesOnly: Boolean): List<*> {
        // SQL에 삽입되는 조건은 고정된 내부 문자열이며 요청 값을 조합하지 않는다.
        val sql =
            RELATED_GAMES_SQL.formatted(
                if (readyCandidatesOnly) READY_CANDIDATE_CONDITION
                else ""
            )
        return entityManager
            .createNativeQuery(sql)
            .setParameter("gameId", gameId)
            .setParameter("limit", limit)
            .resultList
    }

    // SQL 조회 결과의 수치 데이터 객체를 안전하게 BigDecimal 타입으로 변환
    private fun toBigDecimal(value: Any?): BigDecimal {
        return BigDecimal((value as Number).toString())
    }

    companion object {
        private const val READY_CANDIDATE_CONDITION =
            """
              AND EXISTS (
                  SELECT 1
                  FROM related_game_calculations calculation
                  WHERE calculation.game_id = g.id
                    AND calculation.last_success_at IS NOT NULL
              )

        """

        // 연관 추천 게임 5개를 선별하고 계산하기 위한 CTE 기반 Native SQL 구문
        // TODO: 평점 데이터가 충분해지면 최소 평점 개수(5)와 평균 평점 기준(3.5)을 재조정
        // JOIN_FIXED_ORDER로 IGDB 평점 필터를 먼저 적용해 대량의 장르 후보에 대한 games PK 반복 조회를 방지
        private const val RELATED_GAMES_SQL =
            """
        WITH base_genres AS (
            SELECT genre_id
            FROM game_genres
            WHERE game_id = :gameId
        ),
        candidates AS (
            SELECT /*+ JOIN_FIXED_ORDER() */
                   gg.game_id,
                   COUNT(*) AS shared_genre_count
            FROM games g
            JOIN game_genres gg ON gg.game_id = g.id
            JOIN base_genres bg ON bg.genre_id = gg.genre_id
            WHERE gg.game_id <> :gameId
              AND g.igdb_rating >= 70.0
        %s
            GROUP BY gg.game_id
        ),
        rating_stats AS (
            SELECT ug.game_id,
                   COUNT(r.rating) AS rating_count,
                   AVG(r.rating) AS avg_rating
            FROM user_games ug
            JOIN candidates c ON c.game_id = ug.game_id
            JOIN reviews r
              ON r.user_game_id = ug.id
             AND (r.status IS NULL OR r.status = 'ACTIVE')
            WHERE r.rating IS NOT NULL
            GROUP BY ug.game_id
        ),
        eligible_candidates AS (
            SELECT c.game_id,
                   c.shared_genre_count
            FROM candidates c
            LEFT JOIN rating_stats rs ON rs.game_id = c.game_id
            /*
             * TODO: 유저 데이터가 충분해지면 아래 필터링 조건 조정 필요
             *   - 현재: 평가 5개 미만(데이터 부족으로 임시 포함) OR 평균 평점 3.5 이상
             *   - 변경 예시: COALESCE(rs.rating_count, 0) >= 10 AND rs.avg_rating >= 4.0
             */
            WHERE COALESCE(rs.rating_count, 0) < 5
               OR rs.avg_rating >= 3.5
        ),
        positive_users AS (
            SELECT ug.user_id
            FROM user_games ug
            LEFT JOIN reviews r
              ON r.user_game_id = ug.id
             AND (r.status IS NULL OR r.status = 'ACTIVE')
            WHERE ug.game_id = :gameId
              AND (
                  ug.play_status IS NOT NULL
                  OR ug.is_liked = TRUE
                  OR r.rating >= 4.0
              )
        ),
        interaction_scores AS (
            SELECT ug.game_id,
                   SUM(
                       CASE
                           WHEN r.rating >= 4.0 THEN r.rating - 3.0
                           ELSE 0.0
                       END
                       +
                       CASE
                           WHEN ug.is_liked = TRUE THEN 1.0
                           ELSE 0.0
                       END
                   ) AS interaction_score
            FROM positive_users pu
            JOIN user_games ug ON ug.user_id = pu.user_id
            JOIN eligible_candidates c ON c.game_id = ug.game_id
            LEFT JOIN reviews r
              ON r.user_game_id = ug.id
             AND (r.status IS NULL OR r.status = 'ACTIVE')
            WHERE ug.is_liked = TRUE
               OR r.rating >= 4.0
            GROUP BY ug.game_id
        )
        SELECT g.id,
               g.title,
               g.cover_image_url,
               g.igdb_rating,
               (
                   COALESCE(i.interaction_score, 0.0)
                   + c.shared_genre_count * 15.0
                   + g.igdb_rating * 0.5
               ) AS recommendation_score
        FROM eligible_candidates c
        JOIN games g ON g.id = c.game_id
        LEFT JOIN interaction_scores i ON i.game_id = c.game_id
        ORDER BY recommendation_score DESC, g.id ASC
        LIMIT :limit

        """
    }
}
