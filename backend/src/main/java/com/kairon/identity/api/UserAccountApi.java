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

    /**
     * The user's assistant opt-in flags, model override, and tone — a typed
     * projection of {@code app_user.preferences.assistant} for M8's
     * {@code assistant} module, which cannot parse the raw preferences map
     * itself (docs/milestones/M8-assistant-foundations.md D3).
     */
    AssistantPreferencesView assistantPreferences(UserId userId);
}
