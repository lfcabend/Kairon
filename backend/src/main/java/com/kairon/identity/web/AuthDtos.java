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

    record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            UserSummary user) {
    }
}
