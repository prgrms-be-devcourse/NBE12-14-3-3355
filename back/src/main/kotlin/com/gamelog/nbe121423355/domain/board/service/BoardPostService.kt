package com.gamelog.nbe121423355.domain.board.service

import com.gamelog.nbe121423355.domain.notification.service.NotificationService
import com.gamelog.nbe121423355.domain.board.dto.BoardAuthorResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardGameResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardPostPageResponse
import com.gamelog.nbe121423355.domain.board.dto.BoardPostRequest
import com.gamelog.nbe121423355.domain.board.dto.BoardPostResponse
import com.gamelog.nbe121423355.domain.board.entity.BoardPost
import com.gamelog.nbe121423355.domain.board.entity.BoardPostLike
import com.gamelog.nbe121423355.domain.board.entity.BoardPostLikeId
import com.gamelog.nbe121423355.domain.board.repository.BoardCommentRepository
import com.gamelog.nbe121423355.domain.board.repository.BoardPostLikeRepository
import com.gamelog.nbe121423355.domain.board.repository.BoardPostRepository
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.user.entity.User
import com.gamelog.nbe121423355.domain.user.repository.UserFollowRepository
import com.gamelog.nbe121423355.domain.user.repository.UserRepository
import com.gamelog.nbe121423355.global.exception.ServiceException
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BoardPostService(
    private val notificationService: NotificationService,
    private val posts: BoardPostRepository,
    private val comments: BoardCommentRepository,
    private val likes: BoardPostLikeRepository,
    private val users: UserRepository,
    private val follows: UserFollowRepository,
    private val games: GameRepository,
) {
    fun list(viewerId: Long, keyword: String?, after: Long?): BoardPostPageResponse {
        if (after != null && after < 1) throw ServiceException("400-40", "잘못된 게시글 커서입니다.")
        val trimmed = keyword?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.length > KEYWORD_MAX) {
            throw ServiceException("400-40", "검색어는 50자 이하로 입력해 주세요.")
        }
        val rows = posts.list(trimmed?.let(::escapeLike), after, PageRequest.of(0, PAGE_SIZE + 1))
        val visible = rows.take(PAGE_SIZE)
        return BoardPostPageResponse(
            posts = responses(visible, viewerId),
            nextCursor = if (rows.size > PAGE_SIZE) visible.last().id else null,
        )
    }

    fun get(viewerId: Long, postId: Long): BoardPostResponse = responses(listOf(visible(postId)), viewerId).first()

    @Transactional
    fun create(viewerId: Long, request: BoardPostRequest): BoardPostResponse {
        val post =
            posts.save(
                BoardPost(
                    user = activeUser(viewerId),
                    title = requireNotNull(request.title).trim(),
                    content = requireNotNull(request.content).trim(),
                    spoiler = request.spoiler,
                    game = attachableGame(request.gameId),
                )
            )
        return responses(listOf(post), viewerId).first()
    }

    @Transactional
    fun update(viewerId: Long, postId: Long, request: BoardPostRequest): BoardPostResponse {
        val post = lockVisible(postId)
        requireOwner(post.user, viewerId)
        post.edit(
            title = requireNotNull(request.title).trim(),
            content = requireNotNull(request.content).trim(),
            spoiler = request.spoiler,
            game = attachableGame(request.gameId),
        )
        return responses(listOf(post), viewerId).first()
    }

    @Transactional
    fun delete(viewerId: Long, postId: Long) {
        val post = lockVisible(postId)
        requireOwner(post.user, viewerId)
        post.deleteByUser()
        notificationService.cancelBoardPost(postId)
    }

    // 게시글 행을 잠가 같은 글의 좋아요 요청을 줄 세우므로 PK 충돌이 나지 않는다
    @Transactional
    fun like(viewerId: Long, postId: Long, value: Boolean): BoardPostResponse {
        val post = lockVisible(postId)
        val key = BoardPostLikeId(postId, viewerId)
        val exists = likes.existsById(key)
        if (value && !exists) likes.save(BoardPostLike(key))
        if (!value && exists) likes.deleteById(key)
        likes.flush()
        return responses(listOf(post), viewerId).first()
    }

    fun visible(postId: Long): BoardPost =
        posts.findVisibleById(postId) ?: throw ServiceException("404-40", "게시글을 찾을 수 없습니다.")

    fun lockVisible(postId: Long): BoardPost {
        val post = posts.findByIdForUpdate(postId)
        if (post == null || !post.isActive || post.user.isDeleted) {
            throw ServiceException("404-40", "게시글을 찾을 수 없습니다.")
        }
        return post
    }

    fun activeUser(userId: Long): User =
        users.findByIdAndDeletedAtIsNull(userId) ?: throw ServiceException("404-1", "존재하지 않는 유저입니다.")

    fun requireOwner(author: User, viewerId: Long) {
        if (author.id != viewerId) throw ServiceException("403-40", "본인이 작성한 글·댓글만 변경할 수 있습니다.")
    }

    // 검색·상세와 같이 등록된 모든 게임을 첨부할 수 있다
    private fun attachableGame(gameId: Long?): Game? =
        gameId?.let { games.findByIdOrNull(it) ?: throw ServiceException("404-43", "첨부할 게임을 찾을 수 없습니다.") }

    private fun responses(rows: List<BoardPost>, viewerId: Long): List<BoardPostResponse> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { checkNotNull(it.id) }
        val likeRows = likes.counts(ids, viewerId).associateBy { it.postId }
        val commentCounts = comments.countsByPost(ids).associate { it.id to it.count }
        val authorIds = rows.mapNotNull { it.user.id }.filter { it != viewerId }.distinct()
        val following =
            if (authorIds.isEmpty()) emptySet()
            else follows.findFolloweeIdsByFollowerIdAndFolloweeIds(viewerId, authorIds).toSet()
        return rows.map { post ->
            val id = checkNotNull(post.id)
            BoardPostResponse(
                id = id,
                title = post.title,
                content = post.content,
                spoiler = post.spoiler,
                edited = post.edited,
                createdDate = post.createdDate,
                author = BoardAuthorResponse.from(post.user),
                mine = post.user.id == viewerId,
                followingAuthor = post.user.id in following,
                game = post.game?.let(BoardGameResponse::from),
                likeCount = likeRows[id]?.count ?: 0,
                liked = (likeRows[id]?.liked ?: 0) > 0,
                commentCount = commentCounts[id] ?: 0,
            )
        }
    }

    companion object {
        const val PAGE_SIZE = 20
        private const val KEYWORD_MAX = 50
    }
}

// LIKE의 %, _ 를 글자 그대로 찾도록 쿼리의 ESCAPE '!'에 맞춰 이스케이프한다
private fun escapeLike(keyword: String): String =
    keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_")
