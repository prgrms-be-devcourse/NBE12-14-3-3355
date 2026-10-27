package com.gamelog.nbe121423355.domain.board.service

import com.gamelog.nbe121423355.domain.board.dto.BoardPostRequest
import com.gamelog.nbe121423355.domain.board.repository.BoardPostLikeRepository
import com.gamelog.nbe121423355.domain.board.repository.BoardPostRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@MySqlIntegrationTest
class BoardPostLikeConcurrencyTest
@Autowired
constructor(
    private val postService: BoardPostService,
    private val users: UserRepository,
    private val posts: BoardPostRepository,
    private val likes: BoardPostLikeRepository,
) {
    @Test
    @DisplayName("같은 글에 좋아요를 동시에 여러 번 눌러도 모두 성공하고 좋아요는 하나만 저장된다")
    fun like_concurrentDuplicateRequests() {
        // given
        val writer = users.save(User("like-writer", "like-writer@test.com", "password"))
        val reader = users.save(User("like-reader", "like-reader@test.com", "password"))
        val postId = checkNotNull(postService.create(checkNotNull(writer.id), BoardPostRequest("제목", "본문")).id)
        val readerId = checkNotNull(reader.id)

        val threadCount = 10
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val failures = ConcurrentLinkedQueue<String>()

        // when
        repeat(threadCount) {
            executor.submit(Runnable {
                ready.countDown()
                try {
                    start.await()
                    postService.like(readerId, postId, true)
                } catch (e: Exception) {
                    failures.add(e.javaClass.simpleName)
                } finally {
                    done.countDown()
                }
            })
        }
        ready.await()
        start.countDown()
        done.await()
        executor.shutdown()

        // then
        assertThat(failures).isEmpty()
        assertThat(likes.count()).isEqualTo(1)
    }

    @AfterEach
    fun tearDown() {
        likes.deleteAll()
        posts.deleteAll()
        users.deleteAll()
    }
}
