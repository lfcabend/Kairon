package com.kairon.common.security;

import tools.jackson.databind.ObjectMapper;

import com.kairon.common.ratelimit.RateLimitFilter;
import com.kairon.common.ratelimit.RateLimitProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
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
 *   <li>A personal-access-token (PAT, M10.5) request — recognized by its fixed
 *       {@code kairon_pat_…} prefix, authenticated by
 *       {@link PersonalAccessTokenAuthenticationFilter} — may reach only a
 *       narrow allowlist (read-only {@code todo}/{@code journal}/{@code projects}
 *       plus {@code /api/v1/assistant/mcp/**}); every other {@code /api/v1/**}
 *       path positively excludes the {@code SCOPE_mcp} authority a PAT alone
 *       carries, so a PAT can never reach e.g. {@code PATCH /me} or the
 *       existing {@code /assistant/**} endpoints. A JWT principal is
 *       unaffected (docs/milestones/M10.5-mcp-server.md D3/D17).</li>
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
            ProblemAuthHandlers problemAuthHandlers, ObjectMapper objectMapper, Environment environment,
            ObjectProvider<PersonalAccessTokenValidator> personalAccessTokenValidators)
            throws Exception {
        // Built here rather than as beans so Spring Boot does not also register them as
        // plain servlet filters outside the security chain. A second, independently
        // configured instance guards /api/v1/assistant/** (kairon.assistant.rate-limit.*,
        // a much tighter budget than the auth-endpoint one above) — bound via Binder
        // rather than a second @ConfigurationProperties bean of the same type, which
        // would need its own qualifier disambiguation for no benefit here
        // (docs/milestones/M8-assistant-foundations.md D11).
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitProperties, objectMapper);
        RateLimitProperties assistantRateLimitProperties = Binder.get(environment)
                .bind("kairon.assistant.rate-limit", RateLimitProperties.class)
                .orElseGet(() -> new RateLimitProperties(false, 0, null, java.util.List.of()));
        RateLimitFilter assistantRateLimitFilter = new RateLimitFilter(assistantRateLimitProperties, objectMapper);
        // An ObjectProvider, not a direct dependency: a sliced @WebMvcTest context that
        // imports this class but never wires the identity module has no such bean, and
        // must still build the exact same filter chain every such test already asserts
        // against — the PAT allowlist below only activates once a real validator exists
        // (i.e. in the full application context).
        PersonalAccessTokenValidator personalAccessTokenValidator = personalAccessTokenValidators.getIfAvailable();
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/v1/auth/**", "/api/v1/ping").permitAll()
                            .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                            .requestMatchers("/actuator/**").denyAll();
                    if (personalAccessTokenValidator != null) {
                        // M10.5 D3/D17 — the PAT allowlist. Both of these are satisfied by
                        // `authenticated()`, true for a Jwt *or* a PatPrincipal, so they must
                        // come before the generic `/api/v1/**` rule below (Spring Security
                        // evaluates matchers top-down, first match wins).
                        auth.requestMatchers(HttpMethod.GET, "/api/v1/todo/**", "/api/v1/journal/**",
                                        "/api/v1/projects/**").authenticated()
                                .requestMatchers("/api/v1/assistant/mcp/**").authenticated()
                                // Everything else under /api/v1/** needs to be authenticated AND not
                                // a PAT (a PAT request to e.g. PATCH /me falls through to here, since
                                // it doesn't match either allowlist rule above, and must be positively
                                // denied rather than let through by a plain authenticated() check).
                                .requestMatchers("/api/v1/**").access(AuthorizationManagers.allOf(
                                        AuthenticatedAuthorizationManager.authenticated(),
                                        AuthorizationManagers.not(
                                                AuthorityAuthorizationManager.hasAuthority("SCOPE_mcp"))));
                    } else {
                        auth.requestMatchers("/api/v1/**").authenticated();
                    }
                    auth.anyRequest().permitAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(problemAuthHandlers)
                        .accessDeniedHandler(problemAuthHandlers)
                        .bearerTokenResolver(new PatAwareBearerTokenResolver())
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemAuthHandlers)
                        .accessDeniedHandler(problemAuthHandlers))
                .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(assistantRateLimitFilter, BearerTokenAuthenticationFilter.class);
        if (personalAccessTokenValidator != null) {
            http.addFilterBefore(new PersonalAccessTokenAuthenticationFilter(personalAccessTokenValidator),
                    BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }
}
