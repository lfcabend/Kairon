package com.kairon.identity.web;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.identity.app.AuthCommands.AuthResult;
import com.kairon.identity.app.AuthCommands.LoginCommand;
import com.kairon.identity.app.AuthCommands.RegisterCommand;
import com.kairon.identity.app.AuthService;
import com.kairon.identity.web.AuthDtos.AuthResponse;
import com.kairon.identity.web.AuthDtos.LoginRequest;
import com.kairon.identity.web.AuthDtos.LogoutRequest;
import com.kairon.identity.web.AuthDtos.RefreshRequest;
import com.kairon.identity.web.AuthDtos.RegisterRequest;
import com.kairon.identity.web.AuthDtos.UserSummary;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/auth} — the only routes that are reachable without an access
 * token, and the only ones that set or read the refresh cookie. Rate-limited by
 * {@code RateLimitFilter}. See docs/ROADMAP.md (M1) and docs/DESIGN.md §6.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest http) {
        log.debug("POST /auth/register email={}", request.email());
        AuthResult result = authService.register(
                new RegisterCommand(request.email(), request.password(), request.displayName(),
                        request.timezoneOrDefault()),
                userAgent(http));
        return withCredentials(HttpStatus.CREATED, result);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {
        log.debug("POST /auth/login email={}", request.email());
        AuthResult result = authService.login(
                new LoginCommand(request.email(), request.password()), userAgent(http));
        return withCredentials(HttpStatus.OK, result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Nullable @RequestBody(required = false) RefreshRequest body,
            HttpServletRequest http) {
        var cookieToken = refreshCookie.read(http);
        String token = cookieToken.orElseGet(() -> body != null ? body.refreshToken() : null);
        log.debug("POST /auth/refresh (cookie-borne={})", cookieToken.isPresent());
        AuthResult result = authService.refresh(token, userAgent(http));
        return withCredentials(HttpStatus.OK, result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Nullable @RequestBody(required = false) LogoutRequest body,
            HttpServletRequest http) {
        String token = refreshCookie.read(http)
                .orElseGet(() -> body != null ? body.refreshToken() : null);
        log.debug("POST /auth/logout");
        authService.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@CurrentUser UserId userId) {
        log.debug("POST /auth/logout-all userId={}", userId.value());
        authService.logoutAll(userId);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withCredentials(HttpStatus status, AuthResult result) {
        ResponseCookie cookie = refreshCookie.issue(result.rawRefreshToken());
        AuthResponse body = new AuthResponse(
                result.accessToken(),
                "Bearer",
                result.accessTokenTtl().toSeconds(),
                UserSummary.from(result.user()));
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    private static String userAgent(HttpServletRequest http) {
        String ua = http.getHeader(HttpHeaders.USER_AGENT);
        if (ua == null) {
            return null;
        }
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }
}
