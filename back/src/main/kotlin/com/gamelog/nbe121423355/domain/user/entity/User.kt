package com.gamelog.nbe121423355.domain.user.entity

import com.gamelog.nbe121423355.global.interop.javaNonNull
import com.gamelog.nbe121423355.global.jpa.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "users")
class User : BaseEntity {
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @jakarta.persistence.Column(unique = true, nullable = false)
    var email: String? = null
        protected set

    @jakarta.persistence.Column(nullable = false)
    var password: String? = null
        protected set

    @jakarta.persistence.Column(unique = true)
    var nickname: String? = null
        protected set

    var profileImageUrl: String? = null
        protected set

    var bio: String? = null
        protected set

    @jakarta.persistence.Column(nullable = false)
    var onboardingCompleted: Boolean = false
        protected set

    var role: String? = null
        protected set

    // 회원탈퇴 시각 (null이면 활성 회원)
    var deletedAt: LocalDateTime? = null
        protected set

    fun completeOnboarding() {
        this.onboardingCompleted = true
    }

    // 회원가입 생성자
    // 회원가입 직후에는 무조건 온보딩 미완료 상태로 시작
    // 회원가입하는 사람은 user뿐
    constructor(nickname: String?, email: String?, password: String?) {
        this.nickname = javaNonNull(nickname, "nickname")
        this.email = javaNonNull(email, "email")
        this.password = javaNonNull(password, "password")
        this.role = "USER"
        this.onboardingCompleted = false
    }

    fun updateProfile(nickname: String?, profileImageUrl: String?, bio: String?) {
        this.nickname = javaNonNull(nickname, "nickname")
        this.profileImageUrl = profileImageUrl
        this.bio = bio
    }

    fun changePassword(encodedPassword: String?) {
        this.password = javaNonNull(encodedPassword, "password")
    }

    fun promoteToAdmin() {
        this.role = "ADMIN"
    }

    fun withdraw(encodedRandomPassword: String?) {
        this.email = "deleted_" + id + "@deleted.local"
        this.nickname = "탈퇴한사용자_" + id
        this.password = javaNonNull(encodedRandomPassword, "password")
        this.profileImageUrl = null
        this.bio = null
        this.deletedAt = LocalDateTime.now()
    }

    val isDeleted: Boolean
        get() = deletedAt != null

    constructor(
        id: Long?,
        email: String?,
        password: String?,
        nickname: String?,
        profileImageUrl: String?,
        bio: String?,
        onboardingCompleted: Boolean,
        role: String?,
        deletedAt: LocalDateTime?,
    ) {
        this.id = id
        this.email = email
        this.password = password
        this.nickname = nickname
        this.profileImageUrl = profileImageUrl
        this.bio = bio
        this.onboardingCompleted = onboardingCompleted
        this.role = role
        this.deletedAt = deletedAt
    }

    constructor()

    val isOnboardingCompleted: Boolean
        get() = onboardingCompleted
}
