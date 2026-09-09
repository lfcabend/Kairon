package com.kairon.identity.api;

import java.util.UUID;

/**
 * The public projection of an account for other modules. Deliberately minimal:
 * feature modules mostly need the id and the {@code timezone} (a "day" is a
 * {@code LocalDate} in the owning user's zone — see docs/DATA_MODEL.md).
 */
public record UserAccountView(
        UUID id,
        String email,
        String displayName,
        String timezone) {
}
