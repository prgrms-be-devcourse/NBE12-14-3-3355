package com.gamelog.nbe121423355.domain.game.service.model

enum class RelatedGamePrecomputeTargetType {
    ALL,
    IDS,
    FAILED,
    UNREADY,
    CHANGED,
}

class RelatedGamePrecomputeTarget @JvmOverloads constructor(
    val type: RelatedGamePrecomputeTargetType,
    gameIds: List<Long> = emptyList(),
    val initializeBaseline: Boolean = false,
) {
    // 호출자가 원본 목록을 변경해도 접수한 실행 범위는 바뀌지 않는다.
    val gameIds: List<Long> = java.util.List.copyOf(gameIds)

    init {
        require(!initializeBaseline || type == RelatedGamePrecomputeTargetType.ALL) { "초기 기준선은 전체 갱신에서만 설정할 수 있습니다." }
        require((type == RelatedGamePrecomputeTargetType.IDS) == gameIds.isNotEmpty()) {
            "지정 ID 실행에만 게임 ID를 입력해야 합니다."
        }
        require(gameIds.all { it > 0 }) {
            "게임 ID는 양수여야 합니다."
        }
    }

    fun normalizedIds(): List<Long> = gameIds.distinct().sorted()
}
