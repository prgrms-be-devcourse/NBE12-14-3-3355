package com.gamelog.nbe121423355.domain.game.entity

/** 미처리 변경의 원인을 합집합으로 보존한다. 저장된 비트 값은 변경하지 않는다. */
enum class RelatedGameChangeReason(val mask: Long) {
    GAME_INFORMATION(1L),
    USER_GAME_RECORD(2L),
    REVIEW(4L),
    GAME_ADDED(8L),
    GAME_DELETED(16L),
    READY(32L),
    INITIAL_BASELINE(64L);

    companion object {
        fun maskOf(reasons: Set<RelatedGameChangeReason>): Long {
            require(reasons.isNotEmpty()) { "변경 이유가 필요합니다." }
            return reasons.fold(0L) { mask, reason -> mask or reason.mask }
        }
    }
}
