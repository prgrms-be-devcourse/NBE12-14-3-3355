package com.gamelog.nbe121423355.global.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

// yaml jwt 설정값 바인딩 파일
// @Value를 사용하게 되면 매번 바인딩해줘야하는데 힘듦
// @Configurationproperties로 한번에 묶어서 바인딩
@ConfigurationProperties(prefix = "jwt")
@JvmRecord
data class JwtProperties(
    val secret: String?,
    val accessTokenValidity: Long,
    val refreshTokenValidity: Long,
) {}
