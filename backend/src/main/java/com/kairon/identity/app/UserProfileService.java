package com.kairon.identity.app;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.UserAccountApi;
import com.kairon.identity.api.UserAccountView;
import com.kairon.identity.domain.AppUser;
import com.kairon.identity.repo.AppUserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the authenticated user's own profile (display name, timezone,
 * preferences), and backs the cross-module {@link UserAccountApi} port.
 */
@Service
public class UserProfileService implements UserAccountApi {

    private final AppUserRepository users;

    public UserProfileService(AppUserRepository users) {
        this.users = users;
    }

    /** Null fields are left unchanged. An invalid IANA zone is a 400. */
    public record UpdateProfileCommand(String displayName, String timezone, Map<String, Object> preferences) {
    }

    public record ProfileView(
            java.util.UUID id,
            String email,
            String displayName,
            String timezone,
            String status,
            Map<String, Object> preferences) {

        static ProfileView of(AppUser u) {
            return new ProfileView(u.getId(), u.getEmail(), u.getDisplayName(), u.getTimezone(),
                    u.getStatus().name(), u.getPreferences());
        }
    }

    @Transactional(readOnly = true)
    public ProfileView get(UserId userId) {
        return ProfileView.of(require(userId));
    }

    @Transactional
    public ProfileView update(UserId userId, UpdateProfileCommand command) {
        AppUser user = require(userId);
        if (command.displayName() != null) {
            String trimmed = command.displayName().trim();
            if (trimmed.isEmpty()) {
                throw ApiException.badRequest("Display name must not be blank.");
            }
            user.rename(trimmed);
        }
        if (command.timezone() != null) {
            user.changeTimezone(validZone(command.timezone()));
        }
        if (command.preferences() != null) {
            user.replacePreferences(command.preferences());
        }
        return ProfileView.of(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccountView> findById(UserId userId) {
        return users.findById(userId.value()).map(AccountViews::of);
    }

    private AppUser require(UserId userId) {
        return users.findById(userId.value())
                .orElseThrow(() -> ApiException.notFound("Account not found."));
    }

    private static String validZone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException ex) {
            throw ApiException.badRequest("Unknown timezone: " + timezone);
        }
    }
}
