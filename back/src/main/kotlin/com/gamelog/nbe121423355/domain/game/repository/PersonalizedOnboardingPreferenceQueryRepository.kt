package com.gamelog.nbe121423355.domain.game.repository

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

// 맞춤 추천에 필요한 사용자의 온보딩 선호 게임·장르 ID를 조회
@Repository
class PersonalizedOnboardingPreferenceQueryRepository(private val entityManager: EntityManager) {
    // 로그인 사용자가 선택한 선호 게임 ID 목록 반환
    fun findPreferredGameIds(userId: Long): List<Long> {
        return entityManager
            .createQuery(PREFERRED_GAME_IDS_JPQL, Long::class.java)
            .setParameter("userId", userId)
            .resultList
    }

    // 로그인 사용자가 선택한 선호 장르 ID 목록 반환
    fun findPreferredGenreIds(userId: Long): List<Long> {
        return entityManager
            .createQuery(PREFERRED_GENRE_IDS_JPQL, Long::class.java)
            .setParameter("userId", userId)
            .resultList
    }

    companion object {
        // 사용자가 온보딩에서 선택한 게임 ID 조회
        private const val PREFERRED_GAME_IDS_JPQL =
            """
        SELECT preference.game.id
        FROM UserPreferenceGame preference
        WHERE preference.user.id = :userId
        ORDER BY preference.game.id

        """

        // 사용자가 온보딩에서 선택한 장르 ID 조회
        private const val PREFERRED_GENRE_IDS_JPQL =
            """
        SELECT preference.genre.id
        FROM UserPreferenceGenre preference
        WHERE preference.user.id = :userId
        ORDER BY preference.genre.id

        """
    }
}
