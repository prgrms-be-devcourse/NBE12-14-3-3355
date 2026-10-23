package com.gamelog.nbe121423355.domain.game.service

import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.entity.RelatedGameCalculation
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCandidateBatchRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCandidateRepository
import com.gamelog.nbe121423355.domain.game.repository.projection.RelatedGameCandidateProjection
import com.gamelog.nbe121423355.global.exception.ServiceException
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(propagation = REQUIRES_NEW)
class RelatedGamePrecomputeStore
constructor(
    private val changeRecorder: RelatedGameChangeRecorder,
    private val entityManager: EntityManager,
    private val gameRepository: GameRepository,
    private val calculationRepository: RelatedGameCalculationRepository,
    private val candidateRepository: RelatedGameCandidateRepository,
    private val candidateBatchRepository: RelatedGameCandidateBatchRepository,
) {
    fun start(gameId: Long, attemptedAt: LocalDateTime?) {
        if (!gameRepository.existsById(gameId)) {
            throw ServiceException("404-1", "존재하지 않는 게임입니다.")
        }
        val calculation =
            calculationRepository
                .findById(gameId)
                .orElseGet({
                    calculationRepository.save(
                        RelatedGameCalculation(
                            entityManager.getReference(
                                Game::class.java,
                                gameId,
                            )
                        )
                    )
                })
        calculation.start(attemptedAt)
    }

    fun replace(
        gameId: Long,
        candidates: MutableList<RelatedGameCandidateProjection>,
        completedAt: LocalDateTime?,
    ) {
        val calculation: RelatedGameCalculation =
            calculationRepository
                .findById(gameId)
                .orElseThrow({ IllegalStateException("계산 시작 상태가 없습니다.") })
        // 이 메서드는 별도 영속성 컨텍스트에서 실행하므로 이전 후보 엔티티를 로딩하지 않는다.
        candidateRepository.deleteAllBySourceGameId(gameId)
        candidateBatchRepository.insertAll(gameId, candidates)
        val wasReady = calculation.isReady
        calculation.succeed(completedAt)
        if (!wasReady) {
            changeRecorder.record(gameId, com.gamelog.nbe121423355.domain.game.entity.RelatedGameChangeReason.READY)
        }
        // JDBC 저장과 JPA 성공 상태 변경이 같은 연결·트랜잭션에서 반영된다.
        entityManager.flush()
    }

    fun fail(gameId: Long, attemptedAt: LocalDateTime?, error: String?) {
        // 계산 중 실제 삭제된 게임에는 외래키를 가진 상태를 다시 생성하지 않는다.
        if (!gameRepository.existsById(gameId)) {
            return
        }
        val calculation: RelatedGameCalculation =
            calculationRepository
                .findById(gameId)
                .orElseThrow({ IllegalStateException("계산 시작 상태가 없습니다.") })
        calculation.fail(attemptedAt, error)
    }
}
