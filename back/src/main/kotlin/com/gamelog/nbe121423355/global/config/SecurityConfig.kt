package com.gamelog.nbe121423355.global.config

import com.gamelog.nbe121423355.global.dto.RsData
import com.gamelog.nbe121423355.global.security.jwt.CorsProperties
import com.gamelog.nbe121423355.global.security.jwt.JwtAuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod.GET
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.*
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.config.http.SessionCreationPolicy.STATELESS
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    @Throws(Exception::class)
    fun filterChain(
        http: HttpSecurity,
        jwtFilter: JwtAuthenticationFilter?,
        objectMapper: ObjectMapper,
        corsConfigurationSource: CorsConfigurationSource?,
    ): SecurityFilterChain? {
        http
            .cors(
                org.springframework.security.config.Customizer { cors: CorsConfigurer<HttpSecurity>
                    ->
                    cors.configurationSource(corsConfigurationSource)
                }
            )
            .csrf(
                org.springframework.security.config.Customizer { csrf: CsrfConfigurer<HttpSecurity>
                    ->
                    csrf.disable()
                }
            )
            .sessionManagement(
                org.springframework.security.config.Customizer {
                    s: SessionManagementConfigurer<HttpSecurity> ->
                    s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                }
            )
            .authorizeHttpRequests(
                org.springframework.security.config.Customizer {
                    auth:
                        AuthorizeHttpRequestsConfigurer<
                            HttpSecurity
                        >.AuthorizationManagerRequestMatcherRegistry ->
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
                            "/api/v1/library/games/profile/*",
                            "/api/v1/library/games/profile/*/games",
                        )
                        .permitAll()
                        .requestMatchers(GET, "/api/v1/library/games/profile")
                        .authenticated()
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
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling(
                org.springframework.security.config.Customizer {
                    exceptionHandling: ExceptionHandlingConfigurer<HttpSecurity> ->
                    exceptionHandling
                        .authenticationEntryPoint(
                            AuthenticationEntryPoint {
                                request: HttpServletRequest?,
                                response: HttpServletResponse,
                                authException:
                                    org.springframework.security.core.AuthenticationException ->
                                writeRsData(
                                    response,
                                    objectMapper,
                                    RsData<Any?>("401-1", "로그인이 필요합니다."),
                                )
                            }
                        )
                        .accessDeniedHandler(
                            AccessDeniedHandler {
                                request: HttpServletRequest?,
                                response: HttpServletResponse,
                                accessDeniedException:
                                    org.springframework.security.access.AccessDeniedException ->
                                writeRsData(
                                    response,
                                    objectMapper,
                                    RsData<Any?>("403-1", "권한이 없습니다."),
                                )
                            }
                        )
                }
            )
        return http.build()
    }

    @Throws(IOException::class)
    private fun writeRsData(
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        rsData: RsData<*>,
    ) {
        response.setContentType("application/json; charset=UTF-8")
        response.setStatus(rsData.statusCode)
        response.getWriter().write(objectMapper.writeValueAsString(rsData))
    }

    // 프론트는 Next.js 프록시로 백엔드를 호출하는 형태로 되어있어서 정상 트래픽은 이 설정을 안 탐 — 직접 호출 대비 안전망
    @Bean
    fun corsConfigurationSource(corsProperties: CorsProperties): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.setAllowedOrigins(corsProperties.allowedOrigins)
        config.setAllowedMethods(
            java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        )
        config.setAllowedHeaders(java.util.List.of("*"))
        config.setAllowCredentials(true)

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
