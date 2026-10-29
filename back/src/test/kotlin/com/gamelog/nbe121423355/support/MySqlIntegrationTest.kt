package com.gamelog.nbe121423355.support

import org.junit.jupiter.api.Tag
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("mysql")
@ActiveProfiles("test")
@Import(MySqlTestConfiguration::class)
annotation class MySqlIntegrationTest
