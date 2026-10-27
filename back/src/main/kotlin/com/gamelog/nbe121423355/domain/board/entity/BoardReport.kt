package com.gamelog.nbe121423355.domain.board.entity

import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

enum class BoardReportTarget {
    POST,
    COMMENT,
}

@Entity
@Table(
    name = "board_reports",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_board_report_target_reporter",
            columnNames = ["target_type", "target_id", "reporter_id"],
        ),
    ],
    indexes = [Index(name = "idx_board_report_status_id", columnList = "status,id")],
)
class BoardReport(
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    val targetType: BoardReportTarget,
    @Column(name = "target_id", nullable = false)
    val targetId: Long,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    val reporter: User,
    @Column(nullable = false, length = REASON_MAX)
    val reason: String,
    // 작성자가 수정·삭제해도 관리자는 신고 당시 원문을 본다
    @Column(name = "title_snapshot", length = BoardPost.TITLE_MAX)
    val titleSnapshot: String?,
    @Column(name = "content_snapshot", nullable = false, columnDefinition = "TEXT")
    val contentSnapshot: String,
    @Column(name = "spoiler_snapshot", nullable = false)
    val spoilerSnapshot: Boolean,
    @Column(name = "author_id", nullable = false)
    val authorId: Long,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReportStatus = ReportStatus.PENDING
        protected set

    fun changeStatus(status: ReportStatus) {
        this.status = status
    }

    companion object {
        const val REASON_MAX = 255
    }
}
