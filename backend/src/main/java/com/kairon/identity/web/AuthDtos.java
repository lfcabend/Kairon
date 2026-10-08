package com.kairon.identity.web;

import java.util.UUID;

import com.kairon.identity.api.UserAccountView;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response bodies for {@code /api/v1/auth}. */
final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(
            @Email @NotBlank @Size(max = 320) String email,
            @NotBlank @Size(min = 10, max = 200) String password,
            @NotBlank @Size(max = 80) String displayName,
            @Size(max = 64) String timezone) {

        String timezoneOrDefault() {
            return timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
        }
    }

    record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    /** Non-web clients may send the refresh token in the body; the web app uses the cookie. */
    record RefreshRequest(String refreshToken) {
    }

    record LogoutRequest(String refreshToken) {
    }

    record UserSummary(UUID id, String email, String displayName, String timezone) {
        static UserSummary from(UserAccountView view) {
            return new UserSummary(view.id(), view.email(), view.displayName(), view.timezone());
        }
    }

    /**
     * {@code refreshToken} (M11 D6) is returned in the body in addition to the
     * existing {@code Set-Cookie} — the web app ignores it (the cookie still
     * does the job); a native client with no cookie jar stores it instead
     * (e.g. via the platform Keystore) and sends it back in
     * {@link RefreshRequest#refreshToken} / {@link LogoutRequest#refreshToken}.
     */
    record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String refreshToken,
            UserSummary user) {
    }
}
