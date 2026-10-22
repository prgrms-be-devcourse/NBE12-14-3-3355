package com.gamelog.nbe121423355.domain.game.init

import com.gamelog.nbe121423355.domain.game.service.GameCollectionService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Order(10)
@Component
@Profile("local")
@ConditionalOnProperty(name = ["igdb.import.enabled"], havingValue = "true")
class GameImportRunner constructor(private val gameCollectionService: GameCollectionService) :
    CommandLineRunner {

    override fun run(vararg args: String) {
        gameCollectionService.importGames()
        log.info("IGDB 동기화를 시작했습니다. 관리자 화면에서 진행 상황을 확인하세요.")
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameImportRunner::class.java)
    }
}
