package com.wafflestudio.team8server.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }.exceptionHandling { exception ->
                // 인증 실패 시 401 Unauthorized 반환
                exception.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                }
            }.authorizeHttpRequests { auth ->
                // URL별 권한 설정
                auth
                    // CORS Preflight 요청 (OPTIONS)
                    .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    // 관리자 전용 API
                    .requestMatchers(
                        "/api/courses/import",
                        "/api/courses/course-sync/**",
                        "/api/admin/**",
                        "/api/v1/syncwithsite/run",
                        "/api/v1/syncwithsite/auto/**",
                    ).hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/notices",
                    ).hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.PUT,
                        "/api/notices/{noticeId}",
                    ).hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.DELETE,
                        "/api/notices/{noticeId}",
                    ).hasRole("ADMIN")
                    // 인증 없이 접근 가능한 API
                    .requestMatchers(
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/kakao/login",
                        "/api/auth/google/login",
                        "/api/courses/search",
                        "/api/leaderboard",
                        "/api/leaderboard/weekly",
                        "/api/v1/health",
                        // Prometheus 스크래핑 — 클러스터 내부 접근만 허용, 외부는 Istio VirtualService에서 차단
                        "/api/v1/prometheus",
                        "/api/v1/syncwithsite/sugang-period",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        // DB 스키마 문서(SchemaSpy) 정적 리소스 — 인증 없이 열람
                        "/schema",
                        "/schema/**",
                    ).permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/notices",
                        "/api/notices/{noticeId}",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }
            // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 이전에 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins =
            listOf(
                "https://snuclear.wafflestudio.com",
                "https://snuclear-dev.wafflestudio.com",
                "http://localhost:3000",
                "http://localhost:5173",
            )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
