package com.gamelog.nbe121423355.domain.user.repository

import com.gamelog.nbe121423355.domain.user.entity.User
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByNicknameContainingIgnoreCaseAndDeletedAtIsNull(
        nickname: String?,
        pageable: Pageable?,
    ): Page<User>

    fun findByNicknameContainingIgnoreCaseAndIdNotAndDeletedAtIsNull(
        nickname: String?,
        excludedUserId: Long?,
        pageable: Pageable?,
    ): Page<User>

    fun findByIdAndDeletedAtIsNull(id: Long?): Optional<User>

    fun existsByIdAndDeletedAtIsNull(id: Long?): Boolean

    fun findByEmail(email: String?): Optional<User>

    fun existsByEmail(email: String?): Boolean

    fun existsByNickname(nickname: String?): Boolean
}
