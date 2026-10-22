package com.gamelog.nbe121423355.domain.user.service;

import com.gamelog.nbe121423355.domain.user.dto.FollowUserResponseDto;
import com.gamelog.nbe121423355.domain.user.entity.User;
import com.gamelog.nbe121423355.domain.user.entity.UserFollow;
import com.gamelog.nbe121423355.global.exception.ServiceException;
import com.gamelog.nbe121423355.support.MySqlIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@MySqlIntegrationTest
@Transactional
class UserSearchIntegrationTest {
    @Autowired UserFollowService service;
    @Autowired EntityManager em;

    @Test
    void searchUsersReturnsPaginationAndViewerFollowState() {
        User viewer = saveUser("search-viewer");
        User third = saveUser("search-c");
        User first = saveUser("search-a");
        User second = saveUser("search-b");
        second.updateProfile("search-b", "profile.png", null);
        saveUser("unrelated");
        User withdrawn = saveUser("search-withdrawn");
        withdrawn.withdraw("random-password");
        // 검색어가 포함된 탈퇴 닉네임으로도 deletedAt 필터 자체를 검증한다.
        withdrawn.updateProfile("search-withdrawn", null, null);
        em.persist(new UserFollow(viewer, second));
        em.flush();
        em.clear();

        var page = service.searchUsers(viewer.getId(), " SEARCH ", 0, 2);
        assertThat(page.users()).extracting(FollowUserResponseDto::userId)
                .containsExactly(first.getId(), second.getId());
        assertThat(page.page()).isZero();
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.users()).allSatisfy(user -> {
            assertThat(user.me()).isFalse();
            assertThat(user.followedAt()).isNull();
        });
        assertThat(page.users().get(0).followedByMe()).isFalse();
        assertThat(page.users().get(1).followedByMe()).isTrue();
        assertThat(page.users().get(1).profileImageUrl()).isEqualTo("profile.png");

        var last = service.searchUsers(viewer.getId(), "search", 1, 2);
        assertThat(last.users()).extracting(FollowUserResponseDto::userId)
                .containsExactly(third.getId());
        assertThat(last.page()).isEqualTo(1);
        assertThat(last.totalElements()).isEqualTo(3);
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void anonymousSearchReturnsMatchingUsersWithoutFollowState() {
        User user = saveUser("search-anonymous");
        em.flush();
        em.clear();
        var page = service.searchUsers(null, "anonymous", 0, 10);
        assertThat(page.users()).extracting(FollowUserResponseDto::userId)
                .containsExactly(user.getId());
        assertThat(page.users().getFirst().me()).isFalse();
        assertThat(page.users().getFirst().followedByMe()).isFalse();
        assertThat(service.searchUsers(null, "no-match", 0, 10).users()).isEmpty();
    }

    @Test
    void searchUsersRejectsInvalidRequests() {
        Long viewerId = saveUser("viewer").getId();
        em.flush();
        em.clear();
        assertThatThrownBy(() -> service.searchUsers(viewerId, "   ", 0, 10))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.searchUsers(viewerId, "a".repeat(51), 0, 10))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.searchUsers(viewerId, "user", -1, 10))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.searchUsers(viewerId, "user", 0, 51))
                .isInstanceOf(ServiceException.class);
    }

    private User saveUser(String nickname) {
        User user = new User(nickname, nickname + "@search.test", "password");
        em.persist(user);
        return user;
    }
}
