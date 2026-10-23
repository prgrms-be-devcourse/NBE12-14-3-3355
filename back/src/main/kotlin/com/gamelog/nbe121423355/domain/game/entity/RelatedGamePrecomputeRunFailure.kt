package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "related_game_precompute_run_failures",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_precompute_failure_run_game",
        columnNames = ["run_id", "game_id"],
    )],
)
class RelatedGamePrecomputeRunFailure protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "run_id", nullable = false,
        foreignKey = ForeignKey(name = "fk_precompute_failure_run"),
    )
    lateinit var run: RelatedGamePrecomputeRun
        protected set

    // 게임 삭제 후에도 과거 실패 대상을 확인할 수 있도록 Game 외래키를 두지 않는다.
    @Column(name = "game_id", nullable = false)
    var gameId: Long = 0
        protected set

    @Column(nullable = false, length = 500)
    lateinit var error: String
        protected set

    @Column(name = "occurred_at", nullable = false)
    lateinit var occurredAt: LocalDateTime
        protected set

    constructor(
        run: RelatedGamePrecomputeRun,
        gameId: Long,
        error: String,
        occurredAt: LocalDateTime,
    ) : this() {
        require(gameId > 0)
        this.run = run
        this.gameId = gameId
        this.error = error.take(500)
        this.occurredAt = occurredAt
    }
}
