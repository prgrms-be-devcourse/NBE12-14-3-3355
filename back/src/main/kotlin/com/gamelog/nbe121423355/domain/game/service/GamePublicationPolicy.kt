package com.gamelog.nbe121423355.domain.game.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GamePublicationPolicy {
    // 추천 후보·인기 게임의 준비 상태 정책이며, 검색과 상세 접근은 제한하지 않는다.
    @Value("\${recommendation.publication.ready-only:false}") val isReadyOnly: Boolean = false
}
