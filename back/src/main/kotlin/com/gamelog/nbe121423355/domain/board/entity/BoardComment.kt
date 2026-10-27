package com.gamelog.nbe121423355.domain.board.entity

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "board_comments",
    indexes = [Index(name = "idx_board_comment_post_parent_id", columnList = "post_id,parent_id,id")],
)
class BoardComment(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    val post: BoardPost,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    val parent: BoardComment?,
    content: String,
    spoiler: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = content
        protected set

    @Column(nullable = false)
    var spoiler: Boolean = spoiler
        protected set

    @Column(nullable = false)
    var edited: Boolean = false
        protected set

    @Column(nullable = false)
    var deleted: Boolean = false
        protected set

    @Column(nullable = false)
    var hidden: Boolean = false
        protected set

    // 본인 삭제와 관리자 숨김 모두 작성자·본문을 가린다
    val isRemoved: Boolean
        get() = deleted || hidden

    fun edit(content: String, spoiler: Boolean) {
        this.content = content
        this.spoiler = spoiler
        this.edited = true
    }

    fun delete() {
        deleted = true
    }

    fun hideByAdmin() {
        hidden = true
    }

    companion object {
        const val CONTENT_MAX = 1000
    }
}
