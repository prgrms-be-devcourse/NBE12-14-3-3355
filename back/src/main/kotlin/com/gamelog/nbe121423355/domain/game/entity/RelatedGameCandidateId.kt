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

    // 두 게임 ID의 순서까지 같아야 같은 추천 후보 키다.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RelatedGameCandidateId || !other.canEqual(this)) return false
        return sourceGameId == other.sourceGameId && candidateGameId == other.candidateGameId
    }

    protected fun canEqual(other: Any?): Boolean = other is RelatedGameCandidateId

    override fun hashCode(): Int {
        // JPA가 무인자 생성 후 ID를 채우는 동안의 null과 기존 해시 계산 규칙을 보존한다.
        var result = 1
        result = result * 59 + (sourceGameId?.hashCode() ?: 43)
        result = result * 59 + (candidateGameId?.hashCode() ?: 43)
        return result
    }

    protected constructor()

    constructor(sourceGameId: Long, candidateGameId: Long) {
        this.sourceGameId = sourceGameId
        this.candidateGameId = candidateGameId
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
