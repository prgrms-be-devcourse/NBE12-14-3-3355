package com.gamelog.nbe121423355.domain.game.init

import com.gamelog.nbe121423355.domain.game.controller.GameDetailController
import com.gamelog.nbe121423355.domain.game.dto.IgdbGameResponse
import com.gamelog.nbe121423355.domain.game.entity.Game
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCalculationRepository
import com.gamelog.nbe121423355.domain.game.service.RelatedGamePrecomputeBatchService
import com.gamelog.nbe121423355.domain.game.service.model.RelatedGamePrecomputeMode
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.mysql.MySQLContainer

class RelatedGamePrecomputeCommandTest {
    @Test
    fun standaloneConfigurationRunsWithoutWebServerOrExternalApiCredentials() {
        // given: 독립 MySQL 컨테이너와 외부 API 자격증명 없는 비웹 CLI 컨텍스트를 준비한다.
        MySQLContainer("mysql:8.4")
            .withDatabaseName("recommendation_command_test")
            .withUsername("test").withPassword("test").use { mysql ->
                mysql.start()
                SpringApplicationBuilder(RelatedGamePrecomputeCommand.CommandConfiguration::class.java)
                    .web(WebApplicationType.NONE)
                    .run(
                        "--spring.profiles.active=test,recommendation-precompute",
                        "--spring.datasource.url=${mysql.jdbcUrl}",
                        "--spring.datasource.username=test", "--spring.datasource.password=test",
                        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "--spring.sql.init.mode=never", "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                    ).use { context ->
                        val em = context.getBean(EntityManager::class.java)
                        val transactions = TransactionTemplate(context.getBean(PlatformTransactionManager::class.java))
                        val gameId = requireNotNull(transactions.execute {
                            val game = Game.createFromIgdb(
                                IgdbGameResponse(
                                    id = 9900001L, name = "Command fixture", summary = null, cover = null,
                                    first_release_date = 1704067200L, rating = null,
                                    involved_companies = mutableListOf(), genres = mutableListOf(),
                                    platforms = mutableListOf(), collections = mutableListOf(),
                                )
                            )
                            em.persist(game)
                            requireNotNull(game.id)
                        })

                        // when: CLI 컨텍스트에서 전체 최초 계산을 실행한다.
                        val result = context.getBean(RelatedGamePrecomputeBatchService::class.java)
                            .run(true, emptyList(), RelatedGamePrecomputeMode.INITIAL_LOAD)

                        // then: 한 게임의 계산이 성공하고 웹 Controller 없이 성공 상태를 저장한다.
                        assertThat(result.exitCode()).isZero()
                        assertThat(result.succeeded).isEqualTo(1)
                        assertThat(context.getBean(RelatedGameCalculationRepository::class.java)
                            .existsByGameIdAndLastSuccessAtIsNotNull(gameId)).isTrue()
                        assertThat(context.getBeansOfType(GameDetailController::class.java)).isEmpty()
                    }
            }
    }
}
