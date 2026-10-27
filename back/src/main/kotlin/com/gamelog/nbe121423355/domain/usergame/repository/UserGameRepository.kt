package com.gamelog.nbe121423355.domain.usergame.repository

import com.gamelog.nbe121423355.domain.review.repository.ReviewRepository
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameGenreDTO
import com.gamelog.nbe121423355.domain.usergame.dto.UserGameScatterDto
import com.gamelog.nbe121423355.domain.usergame.entity.UserGame
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserGameRepository : JpaRepository<UserGame, Long> {
    fun findByUser_IdAndGame_Id(userId: Long, gameId: Long): UserGame?

    fun findAllByUser_IdAndGame_IdInAndInLibraryTrue(
        userId: Long,
        gameIds: List<Long>,
    ): List<UserGame>

    @Query(
        value =
            "SELECT ug $LIBRARY_FILTERS" +
                """
                ORDER BY
                  CASE WHEN :sort = 'RECENT_PLAYED' AND ug.lastPlayedAt IS NULL THEN 1 ELSE 0 END,
                  CASE WHEN :sort = 'RECENT_PLAYED' THEN ug.lastPlayedAt END DESC,
                  CASE WHEN :sort = 'RATING' AND r.rating IS NULL THEN 1 ELSE 0 END,
                  CASE WHEN :sort = 'RATING' THEN r.rating END DESC,
                  CASE WHEN :sort = 'TITLE' THEN g.title END ASC,
                  CASE WHEN :sort = 'PLAY_TIME' AND ug.playTimeHours IS NULL THEN 1 ELSE 0 END,
                  CASE WHEN :sort = 'PLAY_TIME' THEN ug.playTimeHours END DESC,
                  ug.id DESC
    
                """,
        countQuery = "SELECT COUNT(ug) $LIBRARY_FILTERS",
    )
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = ["game"])
    fun findByLibraryFilters(
        userId: Long,
        status: String,
        keyword: String?,
        filterPlatforms: Boolean,
        platformIds: List<Long>,
        filterGenres: Boolean,
        genreIds: List<Long>,
        sort: String,
        pageable: Pageable,
    ): Page<UserGame>

    @Query("SELECT ug FROM UserGame ug WHERE ug.user.id = :userId AND ug.inLibrary = true AND $PLAYED_GAME")
    fun findPlayedGames(userId: Long): List<UserGame>

    @Query(
        """
    SELECT new com.gamelog.nbe121423355.domain.usergame.dto.UserGameScatterDto(
        g.id,
        g.title,
        g.coverImageUrl,
        ug.playTimeHours,
        r.rating
    )
    FROM UserGame ug
    JOIN ug.game g
        $ACTIVE_REVIEW_JOIN
    WHERE ug.user.id = :userId
      AND ug.inLibrary = true
      AND ug.playTimeHours > 0
      AND r.rating IS NOT NULL

"""
    )
    fun findPlayedGameScatterData(userId: Long): List<UserGameScatterDto>

    @Query(
        """
        SELECT new com.gamelog.nbe121423355.domain.usergame.dto.UserGameGenreDTO(
            genre.id,
            genre.name,
            COUNT(DISTINCT ug.game.id)
        )
        FROM UserGame ug
        JOIN ug.game game
        JOIN GameGenre gg ON gg.game = game
        JOIN gg.genre genre
        WHERE ug.user.id = :userId
          AND ug.inLibrary = true
          AND $PLAYED_GAME
        GROUP BY genre.id, genre.name
        ORDER BY COUNT(DISTINCT ug.game.id) DESC

    """
    )
    fun findGenreDistribution(userId: Long): List<UserGameGenreDTO>

    @Query(
        """
    SELECT ug
    FROM UserGame ug
    JOIN FETCH ug.game
    WHERE ug.user.id = :userId
      AND ug.inLibrary = true
      AND ug.lastPlayedAt IS NOT NULL
    ORDER BY ug.lastPlayedAt DESC

"""
    )
    fun findRecentPlayedGames(
        userId: Long,
        pageable: Pageable,
    ): List<UserGame>

    companion object {
        const val ACTIVE_REVIEW_JOIN: String =
            " LEFT JOIN Review r ON r.userGame = ug AND ${ReviewRepository.ACTIVE_REVIEW} "
        const val PLAYED_GAME: String =
            """
            (ug.playStatus IS NOT NULL OR ug.playing = true OR ug.playTimeHours > 0
                OR EXISTS (SELECT r.id FROM Review r WHERE r.userGame = ug
                    AND r.rating IS NOT NULL AND
                    ${ReviewRepository.ACTIVE_REVIEW})) """

        // EXISTS prevents multi-genre games from duplicating rows and corrupting pagination.
        const val LIBRARY_FILTERS: String =
            """
            FROM UserGame ug
            JOIN ug.game g

            $ACTIVE_REVIEW_JOIN 
            WHERE ug.user.id = :userId
              AND (ug.inLibrary = true OR (:status = 'LIKED' AND ug.liked = true))
              AND (:status = 'ALL'
                OR (:status = 'PLAYED' AND ug.playStatus IS NOT NULL)
                OR (:status = 'PLAYING' AND ug.playing = true)
                OR (:status = 'BACKLOG' AND ug.backlog = true)
                OR (:status = 'WISHLIST' AND ug.wishlist = true)
                OR (:status = 'LIKED' AND ug.liked = true))
              AND (:keyword IS NULL OR LOWER(g.title) LIKE LOWER(:keyword) ESCAPE '!')
              AND (:filterPlatforms = false OR ug.platform.id IN :platformIds)
              AND (:filterGenres = false OR EXISTS (
                SELECT 1 FROM GameGenre gg WHERE gg.game = g AND gg.genre.id IN :genreIds))

            """
    }
}
