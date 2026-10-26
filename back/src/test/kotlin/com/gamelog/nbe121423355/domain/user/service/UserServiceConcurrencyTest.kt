package com.gamelog.nbe121423355.domain.user.service

import com.gamelog.nbe121423355.domain.user.dto.SignupRequestDto
import com.gamelog.nbe121423355.domain.user.dto.UpdateProfileRequestDto
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.upload.ImageUploadService
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
@MySqlIntegrationTest
class UserServiceConcurrencyTest
@Autowired
constructor(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @MockitoBean
    private lateinit var imageUploadService: ImageUploadService

    @Test
    @DisplayName("동시에_같은_이메일로_회원가입하면_하나만_성공")
    fun signUp_concurrentDuplicateEmail() {
        // given
        val threadCount = 2

        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)

        val successCount = AtomicInteger()
        val failureCount = AtomicInteger()

        // when
        for (index in 0 until threadCount) {
            executor.submit(Runnable {
                ready.countDown()

                try {
                    start.await()

                    // 이메일은 동일하게, 닉네임은 다르게 - 이메일 유니크 제약만 정확히 겨냥
                    userService.signUp(
                        SignupRequestDto("race$index", "race@test.com", "password123"),
                    )

                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
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
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(1)

        val savedCount = userRepository.findAll().count { user -> user.email == "race@test.com" }
        assertThat(savedCount).isEqualTo(1)
    }

    @Test
    @DisplayName("동시에_같은_닉네임으로_회원가입하면_하나만_성공")
    fun signUp_concurrentDuplicateNickname() {
        // given
        val threadCount = 2

        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)

        val successCount = AtomicInteger()
        val failureCount = AtomicInteger()

        // when
        for (index in 0 until threadCount) {
            executor.submit(Runnable {
                ready.countDown()

                try {
                    start.await()

                    // 닉네임은 동일하게, 이메일은 다르게 - 닉네임 유니크 제약만 정확히 겨냥
                    userService.signUp(
                        SignupRequestDto("raceNickname", "race$index@test.com", "password123"),
                    )

                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
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
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(1)

        val savedCount = userRepository.findAll().count { user -> user.nickname == "raceNickname" }
        assertThat(savedCount).isEqualTo(1)
    }

    @Test
    @DisplayName("동시에_같은_닉네임으로_프로필을_수정하면_하나만_성공")
    fun updateProfile_concurrentDuplicateNickname() {
        // given
        val userA = userRepository.save(
            User("userA", "userA@test.com", passwordEncoder.encode("password123")),
        )
        val userB = userRepository.save(
            User("userB", "userB@test.com", passwordEncoder.encode("password123")),
        )

        val requestDto = UpdateProfileRequestDto("raceNickname", null, null)

        val threadCount = 2
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)

        val successCount = AtomicInteger()
        val failureCount = AtomicInteger()

        // when
        val userIds = listOf(checkNotNull(userA.id), checkNotNull(userB.id))
        for (userId in userIds) {
            executor.submit(Runnable {
                ready.countDown()

                try {
                    start.await()

                    userService.updateProfile(userId, requestDto)

                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
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
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(1)

        val savedCount = userRepository.findAll().count { user -> user.nickname == "raceNickname" }
        assertThat(savedCount).isEqualTo(1)
    }

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
    }
}
