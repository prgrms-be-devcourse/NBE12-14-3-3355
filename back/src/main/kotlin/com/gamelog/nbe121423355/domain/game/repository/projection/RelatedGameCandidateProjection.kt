package com.gamelog.nbe121423355.domain.game.repository.projection

import java.math.BigDecimal

data class RelatedGameCandidateProjection(val gameId: Long, val relatedScore: BigDecimal)
