package com.gamelog.nbe121423355.domain.game.init

import com.gamelog.nbe121423355.domain.game.repository.GameRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameCandidateBatchRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeSnapshotRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGamePrecomputeTargetRepository
import com.gamelog.nbe121423355.domain.game.repository.RelatedGameQueryRepository
import com.gamelog.nbe121423355.domain.game.service.*
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.WebApplicationType
import org.springframework.boot.WebApplicationType.NONE
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

object RelatedGamePrecomputeCommand {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size == 1 && "--help".equals(args[0])) {
            System.out.println("전체: --all --mode=INITIAL_LOAD|REFRESH")
            System.out.println("지정: --game-ids=10,20 --mode=INITIAL_LOAD|REFRESH")
            System.out.println("DB 주소 변경: SPRING_DATASOURCE_URL 환경변수 사용")
            return
        }
        var exitCode: Int
        try {
            val options: RelatedGamePrecomputeOptions =
                RelatedGamePrecomputeOptions.parse(DefaultApplicationArguments(*args))
            SpringApplicationBuilder(CommandConfiguration::class.java)
                .web(WebApplicationType.NONE)
                .run(
                    "--spring.profiles.active=local,recommendation-precompute",
                    "--spring.sql.init.mode=never",
                    "--spring.jpa.hibernate.ddl-auto=validate",
                    "--spring.jpa.show-sql=false",
                )
                .use { context ->
                    exitCode =
                        context
                            .getBean(RelatedGamePrecomputeBatchService::class.java)
                            .run(options.all, options.gameIds, options.mode)
                            .exitCode()
                }
        } catch (failure: RuntimeException) {
            System.err.println("추천 계산 실행 실패: " + failure.message)
            exitCode = 1
        }
        if (exitCode != 0) {
            System.exit(exitCode)
        }
    }

    // 일반 웹 서버 실행에서는 이 설정을 로딩하지 않는다.
    @Configuration(proxyBeanMethods = false)
    @Profile("recommendation-precompute")
    @EnableAutoConfiguration
    @EntityScan("com.gamelog.nbe121423355.domain")
    @EnableJpaRepositories(basePackageClasses = [GameRepository::class])
    @EnableJpaAuditing
    @Import(
        RelatedGamePrecomputeBatchService::class,
        RelatedGamePrecomputeLock::class,
        RelatedGamePrecomputeService::class,
        RelatedGamePrecomputeStore::class,
        com.gamelog.nbe121423355.domain.game.service.RelatedGameChangePreparation::class,
        com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeExpansion::class,
        com.gamelog.nbe121423355.domain.game.repository.RelatedGameRefreshTargetRepository::class,
        com.gamelog.nbe121423355.domain.game.service.RelatedGameChangeRecorder::class,
        com.gamelog.nbe121423355.domain.game.repository.RelatedGameInputChangeRepository::class,
        RelatedGameQueryRepository::class,
        RelatedGamePrecomputeSnapshotRepository::class,
        RelatedGamePrecomputeTargetRepository::class,
        RelatedGameCandidateBatchRepository::class,
        GamePublicationPolicy::class,
    )
    class CommandConfiguration
}
