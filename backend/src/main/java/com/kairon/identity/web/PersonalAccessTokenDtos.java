package com.kairon.identity.web;

import java.time.Instant;
import java.util.UUID;

import com.kairon.identity.app.PersonalAccessTokenService.PersonalAccessTokenView;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class PersonalAccessTokenDtos {

    private PersonalAccessTokenDtos() {
    }

    record IssuePersonalAccessTokenRequest(@NotBlank @Size(max = 100) String name, Instant expiresAt) {
    }

    /** The raw token appears here, and only here — never again on any later read. */
    record IssuedPersonalAccessTokenResponse(UUID id, String name, String token, Instant createdAt,
            Instant expiresAt) {
    }

    record PersonalAccessTokenResponse(UUID id, String name, Instant createdAt, Instant lastUsedAt,
            Instant expiresAt, boolean revoked) {

        static PersonalAccessTokenResponse from(PersonalAccessTokenView view) {
            return new PersonalAccessTokenResponse(view.id(), view.name(), view.createdAt(), view.lastUsedAt(),
                    view.expiresAt(), view.revoked());
        }
    }
}
