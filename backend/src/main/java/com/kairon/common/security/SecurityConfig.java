package com.kairon.common.security;

import tools.jackson.databind.ObjectMapper;

import com.kairon.common.ratelimit.RateLimitFilter;
import com.kairon.common.ratelimit.RateLimitProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The one security setup for the whole app (docs/DESIGN.md §3.3 / §6):
 *
 * <ul>
 *   <li>Stateless bearer-token API. CSRF is disabled globally because the only
 *       cookie-authenticated route is {@code /api/v1/auth/refresh}, which is
 *       {@code SameSite=Strict}.</li>
 *   <li>{@code /api/v1/auth/**} and {@code /api/v1/ping} are open; every other
 *       {@code /api/v1/**} route needs a valid access token.</li>
 *   <li>Only {@code /actuator/health/**} and {@code /actuator/info} are open; every
 *       other actuator path is denied outright regardless of what
 *       {@code management.endpoints.web.exposure.include} is ever set to, so a
 *       future values/config-only change can't silently make {@code /actuator/env}
 *       or {@code /actuator/prometheus} public (M7 D10).</li>
 *   <li>{@code /v3/api-docs/**} and {@code /swagger-ui/**} are deliberately public,
 *       same reasoning as {@code /actuator/info}: it's documentation, not data
 *       (M7 D13).</li>
 *   <li>Static assets and SPA fallback routes ({@code /login}, …) are open so the
 *       browser can load the app and route client-side.</li>
 *   <li>Auth failures render as {@code application/problem+json}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({ JwtProperties.class, RateLimitProperties.class })
public class SecurityConfig {

    @Bean
    ProblemAuthHandlers problemAuthHandlers(ObjectMapper objectMapper) {
        return new ProblemAuthHandlers(objectMapper);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitProperties rateLimitProperties,
            ProblemAuthHandlers problemAuthHandlers, ObjectMapper objectMapper) throws Exception {
        // Built here rather than as a bean so Spring Boot does not also register it as a
        // plain servlet filter outside the security chain.
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitProperties, objectMapper);
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/api/v1/ping").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(problemAuthHandlers)
                        .accessDeniedHandler(problemAuthHandlers)
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemAuthHandlers)
                        .accessDeniedHandler(problemAuthHandlers))
                .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
