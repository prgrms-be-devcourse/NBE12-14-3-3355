package com.gamelog.nbe121423355.domain.game.repository

import com.gamelog.nbe121423355.domain.game.dto.RelatedGameResponse
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidate
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCandidateId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RelatedGameCandidateRepository :
    JpaRepository<RelatedGameCandidate, RelatedGameCandidateId> {
    fun findByIdSourceGameIdOrderByRelatedRankAsc(
        sourceGameId: Long?
    ): MutableList<RelatedGameCandidate>

    @Query(
        """
            SELECT new com.gamelog.nbe121423355.domain.game.dto.RelatedGameResponse(
                game.id, game.title, game.coverImageUrl, game.igdbRating, candidate.relatedScore, null)
            FROM RelatedGameCandidate candidate
            JOIN candidate.candidateGame game
            WHERE candidate.id.sourceGameId = :sourceGameId
            AND (:#{@gamePublicationPolicy.readyOnly} = false OR EXISTS (
                SELECT 1 FROM RelatedGameCalculation calculation
                WHERE calculation.gameId = game.id AND calculation.lastSuccessAt IS NOT NULL))
            ORDER BY candidate.relatedRank

            """
    )
    fun findStoredRelatedGames(
        @Param("sourceGameId") sourceGameId: Long?,
        pageable: Pageable?,
    ): MutableList<RelatedGameResponse>

    @Modifying
    @Query(
        "DELETE FROM RelatedGameCandidate candidate WHERE candidate.id.sourceGameId = :sourceGameId"
    )
    fun deleteAllBySourceGameId(@Param("sourceGameId") sourceGameId: Long?): Int
}
