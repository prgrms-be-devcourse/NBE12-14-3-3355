package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
class RelatedGameCandidateId : Serializable {
    @Column(name = "source_game_id", nullable = false)
    final var sourceGameId: Long? = null
        private set

    @Column(name = "candidate_game_id", nullable = false)
    final var candidateGameId: Long? = null
        private set

    public override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is RelatedGameCandidateId) return false
        val other = o
        if (!other.canEqual(this as Any)) return false
        val `this$sourceGameId`: Any? = this.sourceGameId
        val `other$sourceGameId`: Any? = other.sourceGameId
        if (
            if (`this$sourceGameId` == null) `other$sourceGameId` != null
            else !`this$sourceGameId`.equals(`other$sourceGameId`)
        )
            return false
        val `this$candidateGameId`: Any? = this.candidateGameId
        val `other$candidateGameId`: Any? = other.candidateGameId
        if (
            if (`this$candidateGameId` == null) `other$candidateGameId` != null
            else !`this$candidateGameId`.equals(`other$candidateGameId`)
        )
            return false
        return true
    }

    protected fun canEqual(other: Any?): Boolean {
        return other is RelatedGameCandidateId
    }

    public override fun hashCode(): Int {
        val PRIME = 59
        var result = 1
        val `$sourceGameId`: Any? = this.sourceGameId
        result = result * PRIME + (if (`$sourceGameId` == null) 43 else `$sourceGameId`.hashCode())
        val `$candidateGameId`: Any? = this.candidateGameId
        result =
            result * PRIME + (if (`$candidateGameId` == null) 43 else `$candidateGameId`.hashCode())
        return result
    }

    protected constructor()

    constructor(sourceGameId: Long?, candidateGameId: Long?) {
        this.sourceGameId = sourceGameId
        this.candidateGameId = candidateGameId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
