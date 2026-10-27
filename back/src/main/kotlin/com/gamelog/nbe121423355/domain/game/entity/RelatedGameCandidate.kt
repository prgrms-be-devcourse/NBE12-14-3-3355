package com.gamelog.nbe121423355.domain.game.entity

import com.gamelog.nbe121423355.global.interop.javaNonNull
import jakarta.persistence.*
import java.math.BigDecimal
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction.CASCADE

@Entity
@Table(
    name = "related_game_candidates",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_related_candidate_source_rank",
                columnNames = ["source_game_id", "related_rank"],
            )
        ],
    check =
        [
            CheckConstraint(
                name = "ck_related_candidate_values",
                constraint =
                    "source_game_id <> candidate_game_id AND related_rank > 0 AND related_score >= 0",
            )
        ],
)
class RelatedGameCandidate {
    @EmbeddedId
    var id: RelatedGameCandidateId? = null
        protected set

    @MapsId("sourceGameId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_game_id", nullable = false)
    @OnDelete(action = CASCADE)
    lateinit var sourceGame: Game
        protected set

    @MapsId("candidateGameId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_game_id", nullable = false)
    @OnDelete(action = CASCADE)
    lateinit var candidateGame: Game
        protected set

    @Column(name = "related_score", nullable = false, precision = 19, scale = 4)
    lateinit var relatedScore: BigDecimal
        protected set

    @Column(name = "related_rank", nullable = false)
    var relatedRank: Int = 0
        protected set

    constructor(sourceGame: Game, candidateGame: Game, relatedScore: BigDecimal, relatedRank: Int) {
        this.sourceGame = sourceGame
        this.candidateGame = candidateGame
        val sourceId = javaNonNull(sourceGame.id, "저장된 기준 게임이 필요합니다.")
        val candidateId = javaNonNull(candidateGame.id, "저장된 후보 게임이 필요합니다.")
        require(sourceId != candidateId) { "기준 게임 자신은 추천 후보가 될 수 없습니다." }
        require(relatedRank >= 1) { "연관 순위는 1 이상이어야 합니다." }
        this.relatedScore = relatedScore
        require(relatedScore.signum() >= 0) { "연관 점수는 음수일 수 없습니다." }
        this.id = RelatedGameCandidateId(sourceId, candidateId)
        this.relatedRank = relatedRank
    }

    protected constructor()
}
