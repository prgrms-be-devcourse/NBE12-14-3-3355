package com.gamelog.nbe121423355.domain.board.dto

import com.gamelog.nbe121423355.domain.board.entity.BoardComment
import com.gamelog.nbe121423355.domain.board.entity.BoardPost
import com.gamelog.nbe121423355.domain.board.entity.BoardReport
import com.gamelog.nbe121423355.domain.review.entity.ReportStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class BoardPostRequest(
    @field:NotBlank(message = "제목을 입력해 주세요.")
    @field:Size(max = BoardPost.TITLE_MAX, message = "제목은 100자 이하로 입력해 주세요.")
    @field:Schema(description = "제목. 공백이 아닌 1~100자", example = "같이 할 협동 게임 추천해 주세요")
    val title: String?,
    @field:NotBlank(message = "본문을 입력해 주세요.")
    @field:Size(max = BoardPost.CONTENT_MAX, message = "본문은 5000자 이하로 입력해 주세요.")
    @field:Schema(description = "본문. 공백이 아닌 1~5000자")
    val content: String?,
    @field:Schema(description = "스포일러 여부. 생략하면 false")
    val spoiler: Boolean = false,
    @field:Schema(description = "첨부할 게임 ID. 생략하거나 null이면 첨부하지 않거나 기존 첨부를 제거합니다.", example = "1")
    val gameId: Long? = null,
)

data class BoardCommentRequest(
    @field:NotBlank(message = "댓글을 입력해 주세요.")
    @field:Size(max = BoardComment.CONTENT_MAX, message = "댓글은 1000자 이하로 입력해 주세요.")
    @field:Schema(description = "댓글 내용. 공백이 아닌 1~1000자")
    val content: String?,
    @field:Schema(description = "스포일러 여부. 생략하면 false")
    val spoiler: Boolean = false,
    @field:Schema(description = "답글을 달 원댓글 ID. 원댓글을 작성하면 생략")
    val parentId: Long? = null,
)

data class BoardCommentUpdateRequest(
    @field:NotBlank(message = "댓글을 입력해 주세요.")
    @field:Size(max = BoardComment.CONTENT_MAX, message = "댓글은 1000자 이하로 입력해 주세요.")
    @field:Schema(description = "댓글 내용. 공백이 아닌 1~1000자")
    val content: String?,
    @field:Schema(description = "스포일러 여부. 생략하면 false")
    val spoiler: Boolean = false,
)

data class BoardReportRequest(
    @field:NotBlank(message = "신고 사유를 입력해 주세요.")
    @field:Size(max = BoardReport.REASON_MAX, message = "신고 사유는 255자 이하로 입력해 주세요.")
    @field:Schema(description = "신고 사유. 공백이 아닌 1~255자", example = "욕설이 포함되어 있습니다.")
    val reason: String?,
)

data class BoardReportStatusRequest(
    @field:NotNull(message = "처리 결과를 선택해 주세요.")
    @field:Schema(description = "처리 결과. APPROVED(승인, 대상 숨김) 또는 REJECTED(반려)", example = "APPROVED")
    val status: ReportStatus?,
)
