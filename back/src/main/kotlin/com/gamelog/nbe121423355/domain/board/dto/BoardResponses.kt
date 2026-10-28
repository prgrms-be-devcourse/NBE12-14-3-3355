package com.gamelog.nbe121423355.domain.board.dto

import com.gamelog.nbe121423355.domain.board.entity.BoardReport
import com.gamelog.nbe121423355.domain.board.entity.BoardReportTarget
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import com.gamelog.nbe121423355.domain.user.entity.User
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.springframework.data.domain.Page

data class BoardAuthorResponse(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String?,
) {
    companion object {
        fun from(user: User) = BoardAuthorResponse(checkNotNull(user.id), user.nickname, user.profileImageUrl)
    }
}

data class BoardGameResponse(
    val id: Long,
    val title: String?,
    val coverImageUrl: String?,
    val releaseDate: LocalDate?,
    @field:Schema(description = "서비스 리뷰 평균 평점. 리뷰가 없으면 null")
    val rating: BigDecimal?,
) {
    companion object {
        fun from(game: Game) =
            BoardGameResponse(checkNotNull(game.id), game.title, game.coverImageUrl, game.releaseDate, game.avgRating)
    }
}

data class BoardPostResponse(
    val id: Long,
    val title: String,
    val content: String,
    val spoiler: Boolean,
    val edited: Boolean,
    val createdDate: LocalDateTime?,
    val author: BoardAuthorResponse,
    @field:Schema(description = "내가 작성한 글이면 true. 팔로우 버튼을 숨길 때 사용")
    val mine: Boolean,
    @field:Schema(description = "내가 작성자를 팔로우 중이면 true")
    val followingAuthor: Boolean,
    val game: BoardGameResponse?,
    val likeCount: Long,
    val liked: Boolean,
    val commentCount: Long,
)

data class BoardPostPageResponse(
    val posts: List<BoardPostResponse>,
    @field:Schema(description = "다음 조회 시 after에 전달할 값. null이면 마지막 페이지")
    val nextCursor: Long?,
)

data class BoardCommentResponse(
    val id: Long,
    val parentId: Long?,
    @field:Schema(description = "삭제·숨김된 댓글이면 null")
    val author: BoardAuthorResponse?,
    @field:Schema(description = "삭제·숨김된 댓글이면 빈 문자열")
    val content: String,
    val spoiler: Boolean,
    @field:Schema(description = "본인 삭제 또는 관리자 숨김. true면 '삭제된 댓글입니다'로 표시")
    val removed: Boolean,
    val edited: Boolean,
    val mine: Boolean,
    val replyCount: Long,
    @field:Schema(description = "작성 일시. 서버 시간대의 UTC 오프셋을 포함한 ISO 8601 형식")
    val createdDate: OffsetDateTime?,
)

data class BoardCommentPageResponse(
    val comments: List<BoardCommentResponse>,
    @field:Schema(description = "다음 조회 시 after에 전달할 값. null이면 마지막 페이지")
    val nextCursor: Long?,
    @field:Schema(description = "게시글의 유효 댓글·답글 수")
    val totalCount: Long,
)

data class BoardReportResponse(
    val id: Long,
    val targetType: BoardReportTarget,
    val targetId: Long,
    val authorId: Long,
    val reporterNickname: String,
    val reason: String,
    val titleSnapshot: String?,
    val contentSnapshot: String,
    val spoilerSnapshot: Boolean,
    val status: ReportStatus,
    val createdDate: LocalDateTime?,
) {
    companion object {
        fun from(report: BoardReport) =
            BoardReportResponse(
                id = checkNotNull(report.id),
                targetType = report.targetType,
                targetId = report.targetId,
                authorId = report.authorId,
                reporterNickname = report.reporter.nickname,
                reason = report.reason,
                titleSnapshot = report.titleSnapshot,
                contentSnapshot = report.contentSnapshot,
                spoilerSnapshot = report.spoilerSnapshot,
                status = report.status,
                createdDate = report.createdDate,
            )
    }
}

data class BoardReportPageResponse(
    val reports: List<BoardReportResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun from(page: Page<BoardReport>) =
            BoardReportPageResponse(
                reports = page.content.map(BoardReportResponse::from),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
    }
}
