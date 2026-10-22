package com.gamelog.nbe121423355.domain.game.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GamePublicationPolicy {
    @Value("\${recommendation.publication.ready-only:false}") val isReadyOnly: Boolean = false
}
