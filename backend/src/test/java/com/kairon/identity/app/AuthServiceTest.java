package com.kairon.identity.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.app.AuthCommands.AuthResult;
import com.kairon.identity.app.AuthCommands.LoginCommand;
import com.kairon.identity.app.AuthCommands.RegisterCommand;
import com.kairon.identity.app.JwtAccessTokenService.MintedAccessToken;
import com.kairon.identity.domain.AppUser;
import com.kairon.identity.domain.RefreshToken;
import com.kairon.identity.repo.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");

    @Mock
    AppUserRepository users;
    @Mock
    RefreshTokenService refreshTokens;
    @Mock
    JwtAccessTokenService accessTokens;
    @Mock
    PasswordEncoder passwordEncoder;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, refreshTokens, accessTokens, passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(accessTokens.mint(any(UserId.class), any()))
                .thenReturn(new MintedAccessToken("access-token", Duration.ofMinutes(15)));
        lenient().when(refreshTokens.issueNewFamily(any(), any(), any()))
                .thenAnswer(inv -> new RefreshTokenService.Issued("raw-refresh",
                        RefreshToken.issue(inv.getArgument(0), "hash", "JUnit", NOW.plusSeconds(3600))));
    }

    @Test
    void registerHashesThePasswordAndIssuesCredentials() {
        when(users.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct horse battery")).thenReturn("{argon2}hashed");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResult result = authService.register(
                new RegisterCommand("Ada@Example.com ", "correct horse battery", " Ada ", "UTC"), "JUnit");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.rawRefreshToken()).isEqualTo("raw-refresh");
        assertThat(result.user().email()).isEqualTo("ada@example.com");
        verify(passwordEncoder).encode("correct horse battery");
    }

    @Test
    void registerRejectsADuplicateEmailWith409() {
        when(users.existsByEmail("ada@example.com")).thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class, () -> authService.register(
                new RegisterCommand("ada@example.com", "correct horse battery", "Ada", "UTC"), "JUnit"));

        assertThat(ex.getStatus().value()).isEqualTo(409);
        verify(users, never()).save(any());
    }

    @Test
    void loginWithAWrongPasswordIs401AndDoesNotRevealWhichPart() {
        AppUser ada = AppUser.register("ada@example.com", "{argon2}stored", "Ada", "UTC");
        when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(ada));
        when(passwordEncoder.matches("wrong", "{argon2}stored")).thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> authService.login(new LoginCommand("ada@example.com", "wrong"), "JUnit"));

        assertThat(ex.getStatus().value()).isEqualTo(401);
        assertThat(ex.getMessage()).isEqualTo("Invalid email or password.");
    }

    @Test
    void loginWithAnUnknownEmailIs401() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginCommand("nobody@example.com", "x"), "JUnit"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));
    }

    @Test
    void refreshRotatesAnActiveTokenAndMintsANewAccessToken() {
        AppUser ada = AppUser.register("ada@example.com", "{argon2}stored", "Ada", "UTC");
        RefreshToken active = RefreshToken.issue(ada.getId(), "hash", "JUnit", NOW.plusSeconds(3600));
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(active));
        when(users.findById(ada.getId())).thenReturn(Optional.of(ada));
        when(refreshTokens.rotate(eq(active), any(), eq(NOW)))
                .thenReturn(new RefreshTokenService.Issued("rotated-raw",
                        active.rotate("newhash", "JUnit", NOW.plusSeconds(7200))));

        AuthResult result = authService.refresh("raw", "JUnit");

        assertThat(result.rawRefreshToken()).isEqualTo("rotated-raw");
        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void refreshOfAnAlreadyRevokedTokenRevokesTheWholeFamilyAndIs401() {
        RefreshToken revoked = RefreshToken.issue(UUID.randomUUID(), "hash", "JUnit", NOW.plusSeconds(3600));
        revoked.revoke(NOW.minusSeconds(10));
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(revoked));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> authService.refresh("raw", "JUnit"));

        assertThat(ex.getStatus().value()).isEqualTo(401);
        verify(refreshTokens).revokeFamily(revoked.getFamilyId(), NOW);
        verify(refreshTokens, never()).rotate(any(), any(), any());
    }

    @Test
    void refreshOfAnExpiredTokenIs401WithoutFamilyRevocation() {
        RefreshToken expired = RefreshToken.issue(UUID.randomUUID(), "hash", "JUnit", NOW.minusSeconds(1));
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw", "JUnit")).isInstanceOf(ApiException.class);
        verify(refreshTokens, never()).revokeFamily(any(), any());
    }

    @Test
    void refreshWithNoTokenIs401() {
        assertThatThrownBy(() -> authService.refresh(null, "JUnit"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(401));
    }

    @Test
    void logoutIsSilentWhenTheTokenIsUnknown() {
        when(refreshTokens.findByRawToken("raw")).thenReturn(Optional.empty());

        authService.logout("raw");

        verify(refreshTokens, never()).revoke(any(), any());
    }

    @Test
    void logoutAllRevokesEveryFamilyForTheUser() {
        UUID id = UUID.randomUUID();
        authService.logoutAll(UserId.of(id));
        verify(refreshTokens).revokeAllForUser(id, NOW);
    }
}
