package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.repository.projection.PersonalizedRelatedGameProjection
import com.gamelog.nbe121423355.domain.game.service.GamePublicationPolicy
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.hibernate.query.NativeQuery
import org.springframework.stereotype.Repository

@Repository
class PersonalizedRelatedGameQueryRepository
constructor(
    private val entityManager: EntityManager,
    private val publicationPolicy: GamePublicationPolicy,
) {
    // 여러 기준 게임의 연관 추천 상위 5개를 한 번에 조회해 Projection으로 반환
    fun findTopFiveBySourceGameIds(
        sourceGameIds: MutableList<Long>,
        excludedGameIds: MutableList<Long>,
    ): MutableList<PersonalizedRelatedGameProjection> {
        return query(
            PersonalizedRelatedGameQueryRepository.RELATED_GAMES_BY_SOURCE_SQL,
            sourceGameIds,
            excludedGameIds,
        )
    }

    fun findStoredTopFiveBySourceGameIds(
        sourceGameIds: MutableList<Long>,
        excludedGameIds: MutableList<Long>,
    ): MutableList<PersonalizedRelatedGameProjection> {
        return query(
            PersonalizedRelatedGameQueryRepository.STORED_RELATED_GAMES_SQL,
            sourceGameIds,
            excludedGameIds,
        )
    }

    private fun query(
        sql: String?,
        sourceGameIds: MutableList<Long>,
        excludedGameIds: MutableList<Long>,
    ): MutableList<PersonalizedRelatedGameProjection> {
        if (sourceGameIds.isEmpty()) {
            return java.util.List.of()
        }
        @SuppressWarnings("unchecked")
        val rows = // 빈 IN 목록을 피하기 위한 존재하지 않는 게임 ID
            entityManager
                .createNativeQuery(sql)
                .unwrap(NativeQuery::class.java)
                .setParameterList("sourceGameIds", sourceGameIds)
                .setParameter("readyOnly", publicationPolicy.isReadyOnly)
                .setParameterList(
                    "excludedGameIds",
                    if (excludedGameIds.isEmpty()) java.util.List.of(-1L) else excludedGameIds,
                )
                .getResultList()
        return rows
            .stream()
            .map({ row ->
                val columns = row as Array<*>
                PersonalizedRelatedGameProjection(
                    (columns[0] as Number).toLong(),
                    (columns[1] as Number).toLong(),
                    columns[2] as String?,
                    columns[3] as String?,
                    toBigDecimal(columns[4]),
                    toBigDecimal(columns[5]),
                    (columns[6] as Number).toInt(),
                )
            })
            .toList()
    }

    // SQL 조회 결과의 수치 데이터 객체를 BigDecimal로 변환
    private fun toBigDecimal(value: Any?): BigDecimal? {
        return if (value == null) null else BigDecimal((value as Number).toString())
    }

    companion object {
        // 사용자별 제외를 먼저 적용하고 저장 순위 순서로 1~5위를 다시 매긴다.
        private const val STORED_RELATED_GAMES_SQL =
            """
        WITH ranked_candidates AS (
            SELECT c.source_game_id, g.id AS game_id, g.title, g.cover_image_url,
                   g.igdb_rating, c.related_score,
                   ROW_NUMBER() OVER (
                       PARTITION BY c.source_game_id ORDER BY c.related_rank
                   ) AS related_rank
            FROM related_game_candidates c
            JOIN related_game_calculations calculation ON calculation.game_id = c.source_game_id
            JOIN games g ON g.id = c.candidate_game_id
            WHERE c.source_game_id IN (:sourceGameIds)
              AND calculation.last_success_at IS NOT NULL
              AND (:readyOnly = false OR EXISTS (
                  SELECT 1 FROM related_game_calculations ready
                  WHERE ready.game_id = g.id AND ready.last_success_at IS NOT NULL))
              AND c.candidate_game_id NOT IN (:excludedGameIds)
        )
        SELECT source_game_id, game_id, title, cover_image_url,
               igdb_rating, related_score, related_rank
        FROM ranked_candidates
        WHERE related_rank <= 5
        ORDER BY source_game_id, related_rank

        """

        // 기존 게임 상세 연관 추천의 점수 기준으로, 기준 게임별 상위 5개 순위를 일괄 조회
        // TODO: 평점 데이터가 충분해지면 최소 평점 개수(5)와 평균 평점 기준(3.5)을 재조정
        // JOIN_FIXED_ORDER로 IGDB 평점 필터를 먼저 적용해 대량의 장르 후보에 대한 games PK 반복 조회를 방지
        private const val RELATED_GAMES_BY_SOURCE_SQL =
            """
        WITH source_genres AS (
            SELECT gg.game_id AS source_game_id,
                   gg.genre_id
            FROM game_genres gg
            WHERE gg.game_id IN (:sourceGameIds)
        ),
        candidates AS (
            SELECT /*+ JOIN_FIXED_ORDER() */
                   sg.source_game_id,
                   gg.game_id,
                   COUNT(*) AS shared_genre_count
            FROM games g
            JOIN game_genres gg ON gg.game_id = g.id
            JOIN source_genres sg ON sg.genre_id = gg.genre_id
            WHERE gg.game_id <> sg.source_game_id
              AND gg.game_id NOT IN (:excludedGameIds)
              AND g.igdb_rating >= 70.0
              AND (:readyOnly = false OR EXISTS (
                  SELECT 1 FROM related_game_calculations ready
                  WHERE ready.game_id = g.id AND ready.last_success_at IS NOT NULL))
            GROUP BY sg.source_game_id, gg.game_id
        ),
        candidate_game_ids AS (
            SELECT DISTINCT game_id
            FROM candidates
        ),
        rating_stats AS (
            SELECT ug.game_id,
                   COUNT(r.rating) AS rating_count,
                   AVG(r.rating) AS avg_rating
            FROM user_games ug
            JOIN candidate_game_ids c ON c.game_id = ug.game_id
            JOIN reviews r
              ON r.user_game_id = ug.id
             AND (r.status IS NULL OR r.status = 'ACTIVE')
            WHERE r.rating IS NOT NULL
            GROUP BY ug.game_id
        ),
        eligible_candidates AS (
            SELECT c.source_game_id,
                   c.game_id,
                   c.shared_genre_count
            FROM candidates c
            LEFT JOIN rating_stats rs ON rs.game_id = c.game_id
            /*
             * 기존 게임 상세 추천과 같은 후보 필터:
             * 평가 5개 미만 OR 평균 평점 3.5 이상
             */
            WHERE COALESCE(rs.rating_count, 0) < 5
               OR rs.avg_rating >= 3.5
        ),
        positive_users AS (
            SELECT DISTINCT ug.game_id AS source_game_id,
                            ug.user_id
            FROM user_games ug
            JOIN (
                SELECT DISTINCT source_game_id
                FROM source_genres
            ) s ON s.source_game_id = ug.game_id
            LEFT JOIN reviews r
              ON r.user_game_id = ug.id
             AND (r.status IS NULL OR r.status = 'ACTIVE')
            WHERE ug.play_status IS NOT NULL
               OR ug.is_liked = TRUE
               OR r.rating >= 4.0
        ),
        interaction_scores AS (
            SELECT pu.source_game_id,
                   ug.game_id,
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
            JOIN eligible_candidates c
              ON c.source_game_id = pu.source_game_id
             AND c.game_id = ug.game_id
            LEFT JOIN reviews r
              ON r.user_game_id = ug.id
             AND (r.status IS NULL OR r.status = 'ACTIVE')
            WHERE ug.is_liked = TRUE
               OR r.rating >= 4.0
            GROUP BY pu.source_game_id, ug.game_id
        ),
        scored_candidates AS (
            SELECT c.source_game_id,
                   g.id AS game_id,
                   g.title,
                   g.cover_image_url,
                   g.igdb_rating,
                   (
                       COALESCE(i.interaction_score, 0.0)
                       + c.shared_genre_count * 15.0
                       + g.igdb_rating * 0.5
                   ) AS related_score
            FROM eligible_candidates c
            JOIN games g ON g.id = c.game_id
            LEFT JOIN interaction_scores i
              ON i.source_game_id = c.source_game_id
             AND i.game_id = c.game_id
        ),
        ranked_candidates AS (
            SELECT sc.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY sc.source_game_id
                       ORDER BY sc.related_score DESC, sc.game_id ASC
                   ) AS related_rank
            FROM scored_candidates sc
        )
        SELECT source_game_id,
               game_id,
               title,
               cover_image_url,
               igdb_rating,
               related_score,
               related_rank
        FROM ranked_candidates
        WHERE related_rank <= 5
        ORDER BY source_game_id ASC, related_rank ASC

        """
    }
}
