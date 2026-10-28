package com.gamelog.nbe121423355.domain.board.service

import com.gamelog.nbe121423355.domain.board.entity.BoardComment
import com.gamelog.nbe121423355.domain.board.entity.BoardPost
import com.gamelog.nbe121423355.domain.board.repository.BoardCommentRepository
import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.user.entity.User
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.json.JsonMapper

@ResourceLock("java.util.TimeZone.default")
class BoardCommentTimeTest {
    @ParameterizedTest
    @ValueSource(strings = ["UTC", "Asia/Seoul"])
    fun `comment JSON preserves the creation instant with an explicit offset`(zone: String) {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            val instant = Instant.parse("2026-10-28T03:00:00Z")
            val user = User("writer", "writer@test.com", "password")
            ReflectionTestUtils.setField(user, "id", 1L)
            val post = BoardPost(user, "title", "body", false, null)
            val comment = BoardComment(post, user, null, "comment", false)
            ReflectionTestUtils.setField(comment, "id", 2L)
            ReflectionTestUtils.setField(comment, "createdDate", LocalDateTime.ofInstant(instant, ZoneId.of(zone)))
            val repository = mock(BoardCommentRepository::class.java)
            `when`(repository.list(1L, null, null, PageRequest.of(0, BoardCommentService.ROOT_PAGE_SIZE + 1)))
                .thenReturn(listOf(comment))
            `when`(repository.replyCounts(listOf(2L))).thenReturn(emptyList())
            val service = BoardCommentService(
                mock(NotificationService::class.java), mock(BoardPostService::class.java), repository,
            )

            val response = service.list(1L, 1L, null, null)
            val mapper = JsonMapper.builder().findAndAddModules().build()
            val json = mapper.readTree(mapper.writeValueAsString(response))
            val timestamp = json.path("comments").path(0).path("createdDate").stringValue()

            assertThat(OffsetDateTime.parse(timestamp).toInstant()).isEqualTo(instant)
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }
}
