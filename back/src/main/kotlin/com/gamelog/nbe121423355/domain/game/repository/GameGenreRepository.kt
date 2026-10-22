package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.entity.GameGenre
import com.gamelog.nbe121423355.domain.game.entity.GameGenreId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GameGenreRepository : JpaRepository<GameGenre, GameGenreId> {
    // 추천 게임들의 장르를 한 번에 조회
    @Query(
        """
            SELECT gameGenre
            FROM GameGenre gameGenre
            JOIN FETCH gameGenre.genre
            WHERE gameGenre.game.id IN :gameIds
            ORDER BY gameGenre.genre.name

            """
    )
    fun findAllWithGenreByGameIdIn(
        @Param("gameIds") gameIds: MutableList<Long>?
    ): MutableList<GameGenre>

    // 게임 ID로 장르 정보를 함께 조회하고 장르 이름순으로 정렬
    @Query(
        """
            SELECT gameGenre
            FROM GameGenre gameGenre
            JOIN FETCH gameGenre.genre
            WHERE gameGenre.game.id = :gameId
            ORDER BY gameGenre.genre.name

            """
    )
    fun findAllWithGenreByGameId(@Param("gameId") gameId: Long?): MutableList<GameGenre>
}
