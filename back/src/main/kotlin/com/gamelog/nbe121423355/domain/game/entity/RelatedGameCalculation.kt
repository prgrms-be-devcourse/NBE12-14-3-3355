package com.gamelog.nbe121423355.domain.game.entity

import com.gamelog.nbe121423355.global.interop.javaNonNull
import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.OnDeleteAction.CASCADE

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "related_game_calculations")
class RelatedGameCalculation {
    @jakarta.persistence.Id
    @jakarta.persistence.Column(name = "game_id")
    var gameId: Long? = null
        protected set

    @MapsId
    @jakarta.persistence.OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    lateinit var game: Game
        protected set

    @jakarta.persistence.Column(name = "last_success_at")
    var lastSuccessAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "last_attempt_status", nullable = false, length = 20)
    lateinit var lastAttemptStatus: RelatedGameCalculationStatus
        protected set

    @jakarta.persistence.Column(name = "last_attempt_at")
    var lastAttemptAt: LocalDateTime? = null
        protected set

    @jakarta.persistence.Column(
        name = "last_error",
        length = RelatedGameCalculation.MAX_ERROR_LENGTH,
    )
    var lastError: String? = null
        protected set

    constructor(game: Game) {
        this.game = javaNonNull(game, "game")
        javaNonNull(game.id, "저장된 게임이 필요합니다.")
        // 새 엔티티는 ID를 비워 persist로 저장하고, @MapsId가 게임 ID를 채우게 한다.
        this.lastAttemptStatus = RelatedGameCalculationStatus.PENDING
    }

    val isReady: Boolean
        get() = lastSuccessAt != null

    fun start(attemptedAt: LocalDateTime?) {
        this.lastAttemptAt = javaNonNull(attemptedAt, "attemptedAt")
        this.lastAttemptStatus = RelatedGameCalculationStatus.RUNNING
        this.lastError = null
    }

    fun succeed(completedAt: LocalDateTime?) {
        requireRunning()
        this.lastSuccessAt = javaNonNull(completedAt, "completedAt")
        this.lastAttemptStatus = RelatedGameCalculationStatus.SUCCESS
        this.lastError = null
    }

    // 실패 트랜잭션이 롤백되어 실행 시작 상태도 유실될 수 있으므로 실패 시각을 함께 받는다.
    fun fail(attemptedAt: LocalDateTime?, error: String?) {
        this.lastAttemptAt = javaNonNull(attemptedAt, "attemptedAt")
        this.lastAttemptStatus = RelatedGameCalculationStatus.FAILED
        this.lastError =
            if (error == null) null
            else
                error.substring(
                    0,
                    Math.min(error.length, RelatedGameCalculation.MAX_ERROR_LENGTH),
                )
        // 기존 정상 결과의 준비 완료 여부는 실패와 독립적으로 유지한다.
    }

    private fun requireRunning() {
        kotlin.check(lastAttemptStatus == RelatedGameCalculationStatus.RUNNING) {
            "실행 중인 계산만 성공 처리할 수 있습니다."
        }
    }

    protected constructor()

    companion object {
        private const val MAX_ERROR_LENGTH = 500
    }
}
