package com.gamelog.nbe121423355.domain.user.entity

import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class RefreshToken protected constructor() : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(unique = true, nullable = false, length = 36)
    lateinit var familyId: String
        protected set

    @Column(nullable = false, length = 36)
    lateinit var currentJti: String
        protected set

    @Column(length = 36)
    var prevJti: String? = null
        protected set

    var rotatedAt: LocalDateTime? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User
        protected set

    @Column(nullable = false)
    lateinit var expiryDate: LocalDateTime
        protected set

    companion object {
        @JvmStatic
        fun create(
            user: User,
            familyId: String,
            jti: String,
            expiryDate: LocalDateTime,
        ): RefreshToken {
            val token = RefreshToken()
            token.user = user
            token.familyId = familyId
            token.currentJti = jti
            token.expiryDate = expiryDate
            return token
        }
    }
}
