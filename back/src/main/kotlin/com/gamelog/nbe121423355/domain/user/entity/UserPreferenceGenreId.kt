package com.gamelog.nbe121423355.domain.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class UserPreferenceGenreId(
    @field:Column(name = "user_id", nullable = false) val userId: Long?,
    @field:Column(name = "genre_id", nullable = false) val genreId: Long?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
