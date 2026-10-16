package com.kairon.common.security;

import java.util.Optional;

/**
 * The SPI {@code common.security} declares so it can recognize a
 * personal-access-token (PAT) bearer credential without importing anything
 * in {@code identity} — {@code identity.app.PersonalAccessTokenService} is
 * the sole implementation (hash lookup, revocation/expiry check, touching
 * {@code lastUsedAt}). {@code common} is the shared kernel every module
 * depends on, never the reverse; JWT validation needs no such SPI because it
 * needs no database lookup at all, but PAT validation inherently does, so it
 * can't be a pure-crypto {@code common} concern the same way (docs/milestones/
 * M10.5-mcp-server.md D15).
 */
public interface PersonalAccessTokenValidator {

    /** Empty if the raw token doesn't hash to any known row, or that row is revoked/expired. */
    Optional<PatPrincipal> validate(String rawToken);
}
