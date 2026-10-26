package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFollow
import com.gamelog.nbe121423355.global.exception.ServiceException
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class UserSearchIntegrationTest
@Autowired
constructor(
    private val service: UserFollowService,
    private val em: EntityManager,
) {
    @Test
    fun searchUsersReturnsPaginationAndViewerFollowState() {
        val viewer = saveUser("search-viewer")
        val third = saveUser("search-c")
        val first = saveUser("search-a")
        val second = saveUser("search-b")
        second.updateProfile("search-b", "profile.png", null)
        saveUser("unrelated")
        val withdrawn = saveUser("search-withdrawn")
        withdrawn.withdraw("random-password")
        // 검색어가 포함된 탈퇴 닉네임으로도 deletedAt 필터 자체를 검증한다.
        withdrawn.updateProfile("search-withdrawn", null, null)
        em.persist(UserFollow(viewer, second))
        em.flush()
        em.clear()

        val page = service.searchUsers(viewer.id, " SEARCH ", 0, 2)
        assertThat(page.users.map { it.userId })
            .containsExactly(first.id, second.id)
        assertThat(page.page).isZero()
        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.totalPages).isEqualTo(2)
        assertThat(page.hasNext).isTrue()
        assertThat(page.users).allSatisfy { user ->
            assertThat(user.me).isFalse()
            assertThat(user.followedAt).isNull()
        }
        assertThat(page.users[0].followedByMe).isFalse()
        assertThat(page.users[1].followedByMe).isTrue()
        assertThat(page.users[1].profileImageUrl).isEqualTo("profile.png")

        val last = service.searchUsers(viewer.id, "search", 1, 2)
        assertThat(last.users.map { it.userId })
            .containsExactly(third.id)
        assertThat(last.page).isEqualTo(1)
        assertThat(last.totalElements).isEqualTo(3)
        assertThat(last.hasNext).isFalse()
    }

    @Test
    fun anonymousSearchReturnsMatchingUsersWithoutFollowState() {
        val user = saveUser("search-anonymous")
        em.flush()
        em.clear()
        val page = service.searchUsers(null, "anonymous", 0, 10)
        assertThat(page.users.map { it.userId })
            .containsExactly(user.id)
        assertThat(page.users.first().me).isFalse()
        assertThat(page.users.first().followedByMe).isFalse()
        assertThat(service.searchUsers(null, "no-match", 0, 10).users).isEmpty()
    }

    @Test
    fun searchUsersRejectsInvalidRequests() {
        val viewerId = saveUser("viewer").id
        em.flush()
        em.clear()
        assertThatThrownBy { service.searchUsers(viewerId, "   ", 0, 10) }
            .isInstanceOf(ServiceException::class.java)
        assertThatThrownBy { service.searchUsers(viewerId, "a".repeat(51), 0, 10) }
            .isInstanceOf(ServiceException::class.java)
        assertThatThrownBy { service.searchUsers(viewerId, "user", -1, 10) }
            .isInstanceOf(ServiceException::class.java)
        assertThatThrownBy { service.searchUsers(viewerId, "user", 0, 51) }
            .isInstanceOf(ServiceException::class.java)
    }

    private fun saveUser(nickname: String): User {
        val user = User(nickname, "$nickname@search.test", "password")
        em.persist(user)
        return user
    }
}
