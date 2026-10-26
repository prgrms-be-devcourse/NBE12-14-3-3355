package com.gamelog.nbe121423355.domain.game.entity

import jakarta.persistence.CheckConstraint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/** JDBC로 원자적으로 변경하는 원천 상태의 읽기 매핑. 삭제된 게임의 정보도 보존한다. */
@Entity
@Immutable
@Table(
    name = "related_game_input_changes",
    check = [CheckConstraint(
        name = "ck_related_input_change_values",
        constraint = "game_id > 0 AND change_version >= 0 AND expanded_version >= 0 " +
            "AND expanded_version <= change_version AND reason_mask >= 0",
    )],
)
class RelatedGameInputChange(
    @Id
    @Column(name = "game_id")
    val gameId: Long,
    @Column(name = "change_version", nullable = false)
    val changeVersion: Long,
    @Column(name = "expanded_version", nullable = false)
    val expandedVersion: Long,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_genre_ids", nullable = false, columnDefinition = "JSON")
    val affectedGenreIds: List<Long>,
    @Column(name = "reason_mask", nullable = false)
    val reasonMask: Long,
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime,
) {
    val isPending: Boolean
        get() = changeVersion > expandedVersion
}
