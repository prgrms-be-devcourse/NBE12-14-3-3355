package com.gamelog.nbe121423355.domain.notification.dto

import com.gamelog.nbe121423355.domain.notification.entity.NotificationKind
import java.time.LocalDateTime
import io.swagger.v3.oas.annotations.media.Schema

data class NotificationResponse(
    @field:Schema(description = "알림 ID", example = "123")
    val id: Long,
    @field:Schema(description = "알림 종류", example = "REVIEW_LIKE")
    val kind: NotificationKind,
    @field:Schema(description = "알림 행위자의 닉네임. 탈퇴·미존재 사용자는 탈퇴한 사용자, 행위자가 없으면 null", example = "게임친구", nullable = true)
    val actorName: String?,
    @field:Schema(description = "알림에 연결된 게임 제목. 게임 정보가 없으면 null", example = "알림 게임", nullable = true)
    val gameTitle: String?,
    @field:Schema(description = "알림 생성 시각", example = "2026-10-27T09:00:00")
    val createdAt: LocalDateTime,
    @field:Schema(description = "읽음 여부", example = "false")
    val read: Boolean,
    @field:Schema(description = "이동할 상대 경로. 대상이 없거나 리뷰 숨김 알림이면 null", example = "/games/1?commentReview=10", nullable = true)
    val url: String?,
    @field:Schema(description = "연결 대상을 이용할 수 없는지 여부. 리뷰 숨김 알림은 false", example = "false")
    val unavailable: Boolean,
)
