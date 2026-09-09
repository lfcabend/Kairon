package com.kairon.identity.api;

import java.util.Optional;

import com.kairon.common.security.UserId;

/**
 * The identity module's public port. Other modules depend only on this interface
 * (and the DTOs in this package), never on {@code identity.domain} or
 * {@code identity.repo} — enforced by ArchUnit (docs/DESIGN.md §3.1).
 */
public interface UserAccountApi {

    Optional<UserAccountView> findById(UserId userId);
}
