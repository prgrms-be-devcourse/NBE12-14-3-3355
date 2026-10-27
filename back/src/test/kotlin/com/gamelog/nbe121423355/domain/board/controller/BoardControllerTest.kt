package com.gamelog.nbe121423355.domain.board.controller

import com.gamelog.nbe121423355.domain.board.repository.BoardReportRepository
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import com.gamelog.nbe121423355.domain.notification.repository.NotificationRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.entity.UserFollow
import com.gamelog.nbe121423355.global.security.jwt.JwtProvider
import com.gamelog.nbe121423355.support.MySqlIntegrationTest
import com.jayway.jsonpath.JsonPath
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@MySqlIntegrationTest
@Transactional
class BoardControllerTest {
    @Autowired private lateinit var em: EntityManager
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var reports: BoardReportRepository
    @Autowired private lateinit var notifications: NotificationRepository

    private lateinit var writer: User
    private lateinit var reader: User
    private lateinit var admin: User
    private lateinit var game: Game

    @BeforeEach
    fun setup() {
        writer = User("작성자", "board-writer@test.com", "password")
        reader = User("독자", "board-reader@test.com", "password")
        admin = User("관리자", "board-admin@test.com", "password").apply { promoteToAdmin() }
        listOf(writer, reader, admin).forEach(em::persist)
        game = Game.createFromIgdb(
            IgdbGameResponse(
                id = 55501L,
                name = "첨부 게임",
                summary = "",
                cover = null,
                first_release_date = 1704067200L,
                rating = BigDecimal.TEN,
                involved_companies = mutableListOf(),
                genres = mutableListOf(),
                platforms = mutableListOf(),
                collections = mutableListOf(),
            )
        )
        em.persist(game)
        em.flush()
    }

    @Test
    @DisplayName("비로그인 사용자는 게시판을 볼 수 없고 일반 회원은 관리자 신고 API를 쓸 수 없다")
    fun requiresLoginAndAdmin() {
        mvc.perform(get(POSTS)).andExpect(status().isUnauthorized)
        mvc.perform(post(POSTS).contentType(MediaType.APPLICATION_JSON).content(postJson("제목", "본문")))
            .andExpect(status().isUnauthorized)
        mvc.perform(get(ADMIN_REPORTS).auth(reader)).andExpect(status().isForbidden)
        mvc.perform(get(ADMIN_REPORTS).auth(admin)).andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-49"))
    }

    @Test
    @DisplayName("제목·본문·댓글 길이와 공백을 검증한다")
    fun validatesInput() {
        for (body in listOf(postJson("   ", "본문"), postJson("x".repeat(101), "본문"), postJson("제목", "  "), postJson("제목", "x".repeat(5001)))) {
            mvc.perform(post(POSTS).auth(writer).json(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }
        mvc.perform(post(POSTS).auth(writer).json(postJson("x".repeat(100), "x".repeat(5000))))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.resultCode").value("201-40"))

        val postId = createPost(writer)
        for (body in listOf(commentJson("   "), commentJson("x".repeat(1001)))) {
            mvc.perform(post(comments(postId)).auth(reader).json(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.resultCode").value("400-1"))
        }
    }

    @Test
    @DisplayName("본인 글·댓글만 수정·삭제할 수 있고 수정 표시가 남는다")
    fun onlyOwnerCanChange() {
        val postId = createPost(writer)
        val commentId = createComment(postId, reader, "댓글")

        mvc.perform(put("$POSTS/$postId").auth(reader).json(postJson("남의 글", "수정")))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-40"))
        mvc.perform(delete("$POSTS/$postId").auth(reader)).andExpect(status().isForbidden)
        mvc.perform(put("${comments(postId)}/$commentId").auth(writer).json(commentJson("남의 댓글")))
            .andExpect(status().isForbidden)

        mvc.perform(put("$POSTS/$postId").auth(writer).json(postJson("새 제목", "새 본문")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-42"))
            .andExpect(jsonPath("$.data.title").value("새 제목"))
            .andExpect(jsonPath("$.data.edited").value(true))
        mvc.perform(put("${comments(postId)}/$commentId").auth(reader).json(commentJson("고친 댓글")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.edited").value(true))
    }

    @Test
    @DisplayName("목록은 최신순 20개씩이고 새 글이 생겨도 다음 페이지 경계가 밀리지 않으며 제목·본문으로 검색한다")
    fun listsWithStableCursorAndSearch() {
        val ids = (1..25).map { createPost(writer, title = "글 $it", content = if (it == 7) "협동 게임 추천" else "본문") }

        val first = mvc.perform(get(POSTS).auth(reader))
            .andExpect(jsonPath("$.resultCode").value("200-40"))
            .andExpect(jsonPath("$.data.posts", hasSize<Any>(20)))
            .andExpect(jsonPath("$.data.posts[0].id").value(ids.last()))
            .andReturn()
        val cursor = JsonPath.read<Number>(first.response.contentAsString, "$.data.nextCursor").toLong()
        assertThat(cursor).isEqualTo(ids[5])

        createPost(writer, title = "중간에 쓴 글")
        mvc.perform(get(POSTS).param("after", cursor.toString()).auth(reader))
            .andExpect(jsonPath("$.data.posts", hasSize<Any>(5)))
            .andExpect(jsonPath("$.data.posts[0].id").value(ids[4]))
            .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))

        mvc.perform(get(POSTS).param("q", "협동").auth(reader))
            .andExpect(jsonPath("$.data.posts", hasSize<Any>(1)))
            .andExpect(jsonPath("$.data.posts[0].id").value(ids[6]))
        mvc.perform(get(POSTS).param("q", "글 2").auth(reader))
            .andExpect(jsonPath("$.data.posts", hasSize<Any>(7)))
        mvc.perform(get(POSTS).param("after", "0").auth(reader))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-40"))
    }

    @Test
    @DisplayName("원댓글은 3개, 답글은 5개씩 오래된 순으로 조회하고 답글에는 답글을 달 수 없다")
    fun pagesCommentsAndRepliesOneLevel() {
        val postId = createPost(writer)
        val roots = (1..4).map { createComment(postId, reader, "원댓글 $it") }
        val replies = (1..6).map { createComment(postId, writer, "답글 $it", parentId = roots[0]) }

        mvc.perform(get(comments(postId)).auth(reader))
            .andExpect(jsonPath("$.resultCode").value("200-46"))
            .andExpect(jsonPath("$.data.comments", hasSize<Any>(3)))
            .andExpect(jsonPath("$.data.comments[0].id").value(roots[0]))
            .andExpect(jsonPath("$.data.comments[0].replyCount").value(6))
            .andExpect(jsonPath("$.data.nextCursor").value(roots[2]))
            .andExpect(jsonPath("$.data.totalCount").value(10))
        mvc.perform(get(comments(postId)).param("after", roots[2].toString()).auth(reader))
            .andExpect(jsonPath("$.data.comments", hasSize<Any>(1)))
            .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
        mvc.perform(get(comments(postId)).param("parentId", roots[0].toString()).auth(reader))
            .andExpect(jsonPath("$.data.comments", hasSize<Any>(5)))
            .andExpect(jsonPath("$.data.comments[4].id").value(replies[4]))
            .andExpect(jsonPath("$.data.nextCursor").value(replies[4]))

        mvc.perform(post(comments(postId)).auth(reader).json(commentJson("답글의 답글", parentId = replies[0])))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.resultCode").value("400-41"))
        mvc.perform(get(comments(postId)).param("parentId", replies[0].toString()).auth(reader))
            .andExpect(jsonPath("$.resultCode").value("400-41"))
    }

    @Test
    @DisplayName("답글이 남은 원댓글을 지우면 자리만 남고, 글을 지우면 글과 댓글 모두 조회되지 않는다")
    fun deletesCommentsAndPosts() {
        val postId = createPost(writer)
        val withReply = createComment(postId, reader, "답글 있는 댓글")
        createComment(postId, writer, "답글", parentId = withReply)
        val alone = createComment(postId, reader, "혼자 있는 댓글")

        mvc.perform(delete("${comments(postId)}/$withReply").auth(reader))
            .andExpect(jsonPath("$.resultCode").value("200-48"))
        mvc.perform(delete("${comments(postId)}/$alone").auth(reader))
        mvc.perform(get(comments(postId)).auth(writer))
            .andExpect(jsonPath("$.data.comments", hasSize<Any>(1)))
            .andExpect(jsonPath("$.data.comments[0].removed").value(true))
            .andExpect(jsonPath("$.data.comments[0].author").value(nullValue()))
            .andExpect(jsonPath("$.data.comments[0].content").value(""))
        mvc.perform(post(comments(postId)).auth(writer).json(commentJson("새 답글", parentId = withReply)))
            .andExpect(jsonPath("$.resultCode").value("400-41"))

        mvc.perform(delete("$POSTS/$postId").auth(writer)).andExpect(jsonPath("$.resultCode").value("200-43"))
        mvc.perform(get("$POSTS/$postId").auth(writer)).andExpect(jsonPath("$.resultCode").value("404-40"))
        mvc.perform(get(comments(postId)).auth(writer)).andExpect(jsonPath("$.resultCode").value("404-40"))
        mvc.perform(get(POSTS).auth(writer)).andExpect(jsonPath("$.data.posts", hasSize<Any>(0)))
    }

    @Test
    @DisplayName("좋아요는 여러 번 눌러도 하나만, 취소도 여러 번 해도 0개다")
    fun likesAreIdempotent() {
        val postId = createPost(writer)
        repeat(2) {
            mvc.perform(put(likes(postId)).auth(reader))
                .andExpect(jsonPath("$.resultCode").value("200-44"))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.liked").value(true))
        }
        mvc.perform(get("$POSTS/$postId").auth(writer))
            .andExpect(jsonPath("$.data.likeCount").value(1))
            .andExpect(jsonPath("$.data.liked").value(false))
        repeat(2) {
            mvc.perform(delete(likes(postId)).auth(reader))
                .andExpect(jsonPath("$.resultCode").value("200-45"))
                .andExpect(jsonPath("$.data.likeCount").value(0))
        }
    }

    @Test
    @DisplayName("신고 당시 원문을 보존하고, 승인하면 같은 대상의 대기 신고를 모두 처리하고 대상을 숨긴다")
    fun reportsKeepSnapshotAndHideTarget() {
        val postId = createPost(writer, title = "원래 제목", content = "원래 본문")
        mvc.perform(post("$POSTS/$postId/reports").auth(writer).json(REASON))
            .andExpect(jsonPath("$.resultCode").value("400-42"))
        val reportId = report("$POSTS/$postId/reports", reader)
        report("$POSTS/$postId/reports", admin)
        mvc.perform(post("$POSTS/$postId/reports").auth(reader).json(REASON))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-40"))

        mvc.perform(put("$POSTS/$postId").auth(writer).json(postJson("바꾼 제목", "바꾼 본문")))
        mvc.perform(get(ADMIN_REPORTS).param("status", "PENDING").auth(admin))
            .andExpect(jsonPath("$.data.reports", hasSize<Any>(2)))
            .andExpect(jsonPath("$.data.reports[1].titleSnapshot").value("원래 제목"))
            .andExpect(jsonPath("$.data.reports[1].contentSnapshot").value("원래 본문"))

        mvc.perform(put("$ADMIN_REPORTS/$reportId/status").auth(admin).json("""{"status":"PENDING"}"""))
            .andExpect(jsonPath("$.resultCode").value("400-43"))
        mvc.perform(put("$ADMIN_REPORTS/$reportId/status").auth(admin).json("""{"status":"APPROVED"}"""))
            .andExpect(jsonPath("$.resultCode").value("200-50"))
        assertThat(reports.findAll()).allMatch { it.status.name == "APPROVED" }
        mvc.perform(get("$POSTS/$postId").auth(reader)).andExpect(jsonPath("$.resultCode").value("404-40"))
        mvc.perform(put("$ADMIN_REPORTS/$reportId/status").auth(admin).json("""{"status":"REJECTED"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.resultCode").value("409-41"))
    }

    @Test
    @DisplayName("댓글 신고를 승인하면 댓글이 숨겨진다")
    fun approvedCommentReportHidesComment() {
        val postId = createPost(writer)
        val commentId = createComment(postId, writer, "문제 댓글")
        val reportId = report("${comments(postId)}/$commentId/reports", reader)

        mvc.perform(put("$ADMIN_REPORTS/$reportId/status").auth(admin).json("""{"status":"APPROVED"}"""))
            .andExpect(jsonPath("$.data.targetType").value("COMMENT"))
            .andExpect(jsonPath("$.data.contentSnapshot").value("문제 댓글"))
        mvc.perform(get(comments(postId)).auth(reader))
            .andExpect(jsonPath("$.data.comments", hasSize<Any>(0)))
    }

    @Test
    @DisplayName("게임을 1개 첨부하고 수정 때 교체·제거할 수 있으며 없는 게임은 404-43이다")
    fun attachesGame() {
        val gameId = requireNotNull(game.id)
        val postId = createPost(writer, gameId = gameId)
        mvc.perform(get("$POSTS/$postId").auth(reader))
            .andExpect(jsonPath("$.data.game.id").value(gameId))
            .andExpect(jsonPath("$.data.game.title").value("첨부 게임"))
            .andExpect(jsonPath("$.data.game.releaseDate").value("2024-01-01"))

        mvc.perform(put("$POSTS/$postId").auth(writer).json(postJson("제목", "본문")))
            .andExpect(jsonPath("$.data.game").value(nullValue()))
        mvc.perform(post(POSTS).auth(writer).json(postJson("제목", "본문", gameId = 999999)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-43"))
    }

    @Test
    @DisplayName("작성자 팔로우 상태와 내 글 여부를 함께 반환한다")
    fun returnsFollowAndMineState() {
        val postId = createPost(writer)
        mvc.perform(get("$POSTS/$postId").auth(reader))
            .andExpect(jsonPath("$.data.mine").value(false))
            .andExpect(jsonPath("$.data.followingAuthor").value(false))
        em.persist(UserFollow(reader, writer))
        em.flush()
        mvc.perform(get(POSTS).auth(reader))
            .andExpect(jsonPath("$.data.posts[0].followingAuthor").value(true))
        mvc.perform(get(POSTS).auth(writer))
            .andExpect(jsonPath("$.data.posts[0].mine").value(true))
            .andExpect(jsonPath("$.data.posts[0].followingAuthor").value(false))
    }

    @Test
    @DisplayName("탈퇴한 사용자의 글은 숨기고 댓글은 익명화된 닉네임으로 남긴다")
    fun hidesWithdrawnAuthorsPosts() {
        val postId = createPost(writer)
        val readerPostId = createPost(reader)
        createComment(readerPostId, writer, "탈퇴 전 댓글")
        writer.withdraw("withdrawn-password")
        em.flush()

        mvc.perform(get("$POSTS/$postId").auth(reader)).andExpect(jsonPath("$.resultCode").value("404-40"))
        mvc.perform(get(POSTS).auth(reader))
            .andExpect(jsonPath("$.data.posts", hasSize<Any>(1)))
            .andExpect(jsonPath("$.data.posts[0].id").value(readerPostId))
        mvc.perform(get(comments(readerPostId)).auth(reader))
            .andExpect(jsonPath("$.data.comments[0].author.nickname").value("탈퇴한사용자_${writer.id}"))
    }

    @Test
    @DisplayName("댓글은 글 작성자에게, 답글은 원댓글 작성자에게도 알리고 댓글을 지우면 알림도 지운다")
    fun notifiesCommentsAndReplies() {
        val postId = createPost(writer)
        val commentId = createComment(postId, reader, "댓글")
        createComment(postId, admin, "답글", parentId = commentId)

        val received = notifications.findAll()
        assertThat(received.filter { it.recipientId == writer.id }.map { it.kind })
            .containsExactlyInAnyOrder(NotificationKind.BOARD_COMMENT, NotificationKind.BOARD_REPLY)
        assertThat(received.filter { it.recipientId == reader.id }.map { it.kind })
            .containsExactly(NotificationKind.BOARD_REPLY)
        mvc.perform(get("/api/v1/notifications").auth(writer))
            .andExpect(jsonPath("$.data.items[0].url").value("/community?board=free&post=$postId"))

        mvc.perform(delete("${comments(postId)}/$commentId").auth(reader))
        assertThat(notifications.findAll().map { it.kind }).containsOnly(NotificationKind.BOARD_REPLY)
        mvc.perform(delete("$POSTS/$postId").auth(writer))
        assertThat(notifications.findAll()).isEmpty()
    }

    private fun createPost(
        user: User,
        title: String = "제목",
        content: String = "본문",
        gameId: Long? = null,
    ): Long = idOf(mvc.perform(post(POSTS).auth(user).json(postJson(title, content, gameId))).andExpect(status().isCreated))

    private fun createComment(postId: Long, user: User, content: String, parentId: Long? = null): Long =
        idOf(mvc.perform(post(comments(postId)).auth(user).json(commentJson(content, parentId))).andExpect(status().isCreated))

    private fun report(path: String, user: User): Long =
        idOf(
            mvc.perform(post(path).auth(user).json(REASON))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.resultCode").value("201-42"))
        )

    private fun idOf(result: ResultActions): Long =
        JsonPath.read<Number>(result.andReturn().response.contentAsString, "$.data.id").toLong()

    private fun MockHttpServletRequestBuilder.auth(user: User): MockHttpServletRequestBuilder =
        header("Authorization", "Bearer ${jwt.generateAccessToken(user.id, user.role)}")

    private fun MockHttpServletRequestBuilder.json(body: String): MockHttpServletRequestBuilder =
        contentType(MediaType.APPLICATION_JSON).content(body)

    private fun postJson(title: String, content: String, gameId: Long? = null): String =
        """{"title":"$title","content":"$content","spoiler":false,"gameId":${gameId ?: "null"}}"""

    private fun commentJson(content: String, parentId: Long? = null): String =
        """{"content":"$content","spoiler":false,"parentId":${parentId ?: "null"}}"""

    private fun comments(postId: Long) = "$POSTS/$postId/comments"

    private fun likes(postId: Long) = "$POSTS/$postId/likes"

    companion object {
        private const val POSTS = "/api/v1/community/posts"
        private const val ADMIN_REPORTS = "/api/v1/admin/board-reports"
        private const val REASON = """{"reason":"부적절한 내용"}"""
    }
}
