package com.gamelog.nbe121423355.domain.board.entity

import com.gamelog.nbe121423355.domain.game.entity.Game
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
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

enum class BoardPostStatus {
    ACTIVE,
    DELETED_BY_USER,
    HIDDEN_BY_ADMIN,
}

@Entity
@Table(
    name = "board_posts",
    indexes = [Index(name = "idx_board_post_status_id", columnList = "status,id")],
)
class BoardPost(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    title: String,
    content: String,
    spoiler: Boolean,
    game: Game?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, length = TITLE_MAX)
    var title: String = title
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = content
        protected set

    @Column(nullable = false)
    var spoiler: Boolean = spoiler
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    var game: Game? = game
        protected set

    @Column(nullable = false)
    var edited: Boolean = false
        protected set

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BoardPostStatus = BoardPostStatus.ACTIVE
        protected set

    var deletedAt: LocalDateTime? = null
        protected set

    val isActive: Boolean
        get() = status == BoardPostStatus.ACTIVE

    fun edit(title: String, content: String, spoiler: Boolean, game: Game?) {
        this.title = title
        this.content = content
        this.spoiler = spoiler
        this.game = game
        this.edited = true
    }

    fun deleteByUser() {
        status = BoardPostStatus.DELETED_BY_USER
        deletedAt = LocalDateTime.now()
    }

    fun hideByAdmin() {
        if (status == BoardPostStatus.ACTIVE) status = BoardPostStatus.HIDDEN_BY_ADMIN
    }

    companion object {
        const val TITLE_MAX = 100
        const val CONTENT_MAX = 5000
    }
}
