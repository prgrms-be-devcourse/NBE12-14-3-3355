package com.gamelog.nbe121423355.domain.board.entity

import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable

@Embeddable
data class BoardPostLikeId(
    @field:Column(name = "post_id", nullable = false) val postId: Long,
    @field:Column(name = "user_id", nullable = false) val userId: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

@Entity
@Table(name = "board_post_likes")
class BoardPostLike(
    @EmbeddedId
    val id: BoardPostLikeId,
) : BaseEntity()
