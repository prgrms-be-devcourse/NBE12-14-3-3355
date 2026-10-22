package com.gamelog.nbe121423355.domain.user.entity

import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@jakarta.persistence.Entity
class RefreshToken protected constructor() : BaseEntity() {
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @jakarta.persistence.Column(unique = true, nullable = false, length = 36)
    lateinit var familyId: String
        protected set

    @jakarta.persistence.Column(nullable = false, length = 36)
    lateinit var currentJti: String
        protected set

    @jakarta.persistence.Column(length = 36)
    var prevJti: String? = null
        protected set

    var rotatedAt: LocalDateTime? = null
        protected set

    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: com.gamelog.nbe121423355.domain.user.entity.User
        protected set

    @jakarta.persistence.Column(nullable = false)
    lateinit var expiryDate: LocalDateTime
        protected set

    companion object {
        @JvmStatic
        fun create(
            user: com.gamelog.nbe121423355.domain.user.entity.User,
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
