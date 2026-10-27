package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction.CASCADE
import org.hibernate.type.SqlTypes

/** 기준 게임별 갱신 요구와 반영 완료 버전. 원천 게임의 버전과는 별개다. */
@Entity
@Immutable
@Table(
    name = "related_game_refresh_targets",
    check = [CheckConstraint(
        name = "ck_related_refresh_target_values",
        constraint = "game_id > 0 AND required_version > 0 AND applied_version >= 0 " +
            "AND applied_version <= required_version AND reason_mask >= 0",
    )],
)
class RelatedGameRefreshTarget(
    @Id
    @Column(name = "game_id")
    val gameId: Long,
    @Column(name = "required_version", nullable = false)
    val requiredVersion: Long,
    @Column(name = "applied_version", nullable = false)
    val appliedVersion: Long,
    @Column(name = "reason_mask", nullable = false)
    val reasonMask: Long,
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime,
) {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "game_id", insertable = false, updatable = false,
        foreignKey = ForeignKey(name = "fk_related_refresh_target_game"),
    )
    @OnDelete(action = CASCADE)
    lateinit var game: Game
        protected set

    val isPending: Boolean
        get() = requiredVersion > appliedVersion
}
