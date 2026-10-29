package com.gamelog.nbe121423355.domain.game.init

import com.gamelog.nbe121423355.domain.game.service.GameTranslationService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Profile("local")
@Order(20)
@ConditionalOnProperty(name = ["game.translation.enabled"], havingValue = "true")
class GameTranslationRunner(private val translations: GameTranslationService) :
    CommandLineRunner {

    override fun run(vararg args: String) {
        translations.translatePending()
    }
}
