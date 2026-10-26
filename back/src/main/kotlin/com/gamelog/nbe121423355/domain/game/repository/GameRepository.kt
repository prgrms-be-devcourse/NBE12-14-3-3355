package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.Game
import java.time.LocalDate
import java.util.Optional
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GameRepository : JpaRepository<Game, Long> {
    @Query("SELECT COALESCE(MAX(game.id), 0) FROM Game game") fun findMaxGameIdForPrecompute(): Long

    @Query(
        """
            SELECT game.id FROM Game game
            WHERE game.id > :afterId AND game.id <= :upperId
            ORDER BY game.id

            """
    )
    fun findGameIdsForPrecompute(
        @Param("afterId") afterId: Long,
        @Param("upperId") upperId: Long,
        pageable: Pageable,
    ): List<Long>

    // 제목 인덱스에서 후보를 먼저 제한하여 큰 본문 컬럼은 최종 6행만 읽습니다.
    @Query(
        value =
            """
        SELECT g.*
        FROM (
            SELECT id, LOWER(title) AS sort_title,
                   CASE
                       WHEN LOWER(title) = LOWER(:keyword) THEN 0
                       WHEN LOWER(title) LIKE LOWER(:prefixPattern) ESCAPE '!' THEN 1
                       ELSE 2
                   END AS match_rank
            FROM games
            WHERE LOWER(title) LIKE LOWER(:containsPattern) ESCAPE '!'
              AND (:#{@gamePublicationPolicy.readyOnly} = false OR EXISTS (
                  SELECT 1 FROM related_game_calculations calculation
                  WHERE calculation.game_id = games.id AND calculation.last_success_at IS NOT NULL))
              AND release_date >= :minReleaseDate
              AND release_date < :maxReleaseDateExclusive
            ORDER BY match_rank, sort_title, id
            LIMIT 6
        ) AS matched
        JOIN games g ON g.id = matched.id
        ORDER BY matched.match_rank, matched.sort_title, matched.id

        """,
        nativeQuery = true,
    )
    fun findSuggestions(
        @Param("keyword") keyword: String?,
        @Param("prefixPattern") prefixPattern: String?,
        @Param("containsPattern") containsPattern: String?,
        @Param("minReleaseDate") minReleaseDate: LocalDate?,
        @Param("maxReleaseDateExclusive") maxReleaseDateExclusive: LocalDate?,
    ): MutableList<Game>

    fun findByIgdbId(igdbId: Long?): Optional<Game>

    // 정렬/페이지 선택을 먼저 수행하여 상세 행 조인을 페이지 크기로 제한합니다.
    @Query(
        value =
            """
                    SELECT g.*
                    FROM (
                        SELECT id, igdb_rating
                        FROM games
                        WHERE release_date >= :minReleaseDate
                          AND (:#{@gamePublicationPolicy.readyOnly} = false OR EXISTS (
                              SELECT 1 FROM related_game_calculations calculation
                              WHERE calculation.game_id = games.id AND calculation.last_success_at IS NOT NULL))
                          AND release_date < :maxReleaseDateExclusive
                          AND (:keyword IS NULL OR LOWER(title) LIKE LOWER(:keyword) ESCAPE '!')
                        ORDER BY COALESCE(igdb_rating, -1.0) DESC, id ASC
                        LIMIT :pageSize OFFSET :pageOffset
                    ) AS matched
                    JOIN games g ON g.id = matched.id
                    ORDER BY COALESCE(g.igdb_rating, -1.0) DESC, g.id ASC

                    """,
        nativeQuery = true,
    )
    fun findDefaultSearchContent(
        @Param("keyword") keyword: String?,
        @Param("minReleaseDate") minReleaseDate: LocalDate?,
        @Param("maxReleaseDateExclusive") maxReleaseDateExclusive: LocalDate?,
        @Param("pageSize") pageSize: Int,
        @Param("pageOffset") pageOffset: Long,
    ): MutableList<Game>

    @Query(
        value =
            """
                    SELECT COUNT(*)
                    FROM games
                    WHERE release_date >= :minReleaseDate
                      AND (:#{@gamePublicationPolicy.readyOnly} = false OR EXISTS (
                          SELECT 1 FROM related_game_calculations calculation
                          WHERE calculation.game_id = games.id AND calculation.last_success_at IS NOT NULL))
                      AND release_date < :maxReleaseDateExclusive
                      AND (:keyword IS NULL OR LOWER(title) LIKE LOWER(:keyword) ESCAPE '!')

                    """,
        nativeQuery = true,
    )
    fun countDefaultSearch(
        @Param("keyword") keyword: String?,
        @Param("minReleaseDate") minReleaseDate: LocalDate?,
        @Param("maxReleaseDateExclusive") maxReleaseDateExclusive: LocalDate?,
    ): Long
}
