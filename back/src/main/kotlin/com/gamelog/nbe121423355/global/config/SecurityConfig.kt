package com.gamelog.nbe121423355.global.config

import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.jwt.CorsProperties
import com.gamelog.nbe121423355.global.security.jwt.JwtAuthenticationFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod.GET
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(
        http: HttpSecurity,
        jwtFilter: JwtAuthenticationFilter?,
        objectMapper: ObjectMapper,
        corsConfigurationSource: CorsConfigurationSource?,
    ): SecurityFilterChain? {
        http
            .cors { cors -> cors.configurationSource(corsConfigurationSource) }
            .csrf { csrf -> csrf.disable() }
            .sessionManagement { s -> s.sessionCreationPolicy(STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        GET,
                        "/actuator/health",
                        "/actuator/prometheus",
                    )
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/users/signup",
                        "/api/v1/users/login",
                        "/api/v1/users/refresh",
                        "/api/v1/users/check-email",
                        "/api/v1/users/check-nickname",
                        "/api/v1/users/logout",
                    )
                    .permitAll()
                    .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**")
                    .authenticated()
                    .requestMatchers(GET, "/api/v1/users/*")
                    .permitAll()
                    .requestMatchers(
                        GET,
                        "/api/v1/users/*/following",
                        "/api/v1/users/*/followers",
                    )
                    .permitAll()
                    .requestMatchers(
                        GET,
                        "/api/v1/users/*/game-profile",
                        "/api/v1/users/*/games",
                    )
                    .permitAll()
                    .requestMatchers(GET, "/api/v1/games/*/reviews/me")
                    .authenticated()
                    .requestMatchers(
                        GET,
                        "/api/v1/games/recommendations/personalized",
                    )
                    .authenticated()
                    .requestMatchers(GET, "/api/v1/reviews/popular")
                    .permitAll()
                    .requestMatchers(
                        GET,
                        "/api/v1/reviews/*/comments",
                        "/api/v1/reviews/*/comments/*",
                    )
                    .permitAll()
                    .requestMatchers(GET, "/api/v1/reviews/*/likes/count")
                    .permitAll()
                    .requestMatchers(GET, "/api/v1/users/*/reviews")
                    .permitAll()
                    .requestMatchers(GET, "/api/v1/games", "/api/v1/games/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN") // 관리자 권한용
                    .anyRequest()
                    .authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint { _, response, _ ->
                        writeRsData(response, objectMapper, RsData<Any?>("401-1", "로그인이 필요합니다."))
                    }
                    .accessDeniedHandler { _, response, _ ->
                        writeRsData(response, objectMapper, RsData<Any?>("403-1", "권한이 없습니다."))
                    }
            }
        return http.build()
    }

    private fun writeRsData(
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        rsData: RsData<*>,
    ) {
        response.contentType = "application/json; charset=UTF-8"
        response.status = rsData.statusCode
        response.writer.write(objectMapper.writeValueAsString(rsData))
    }

    // 프론트는 Next.js 프록시로 백엔드를 호출하는 형태로 되어있어서 정상 트래픽은 이 설정을 안 탐 — 직접 호출 대비 안전망
    @Bean
    fun corsConfigurationSource(corsProperties: CorsProperties): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = corsProperties.allowedOrigins
        config.allowedMethods =
            listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
