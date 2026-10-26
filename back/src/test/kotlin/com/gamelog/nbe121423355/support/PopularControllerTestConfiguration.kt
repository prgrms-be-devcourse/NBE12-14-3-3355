package com.gamelog.nbe121423355.support

import com.gamelog.nbe121423355.global.aspect.ResponseAspect
import com.gamelog.nbe121423355.global.config.SecurityConfig
import com.gamelog.nbe121423355.global.exceptionHandler.GlobalExceptionHandler
import com.gamelog.nbe121423355.global.security.jwt.CorsProperties
import com.gamelog.nbe121423355.global.security.jwt.JwtAuthenticationFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import

// JPA Auditing을 활성화하는 애플리케이션 루트 대신 웹 계층에 필요한 설정만 사용한다.
@TestConfiguration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
@EnableConfigurationProperties(CorsProperties::class)
@Import(
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    GlobalExceptionHandler::class,
    ResponseAspect::class,
)
class PopularControllerTestConfiguration
