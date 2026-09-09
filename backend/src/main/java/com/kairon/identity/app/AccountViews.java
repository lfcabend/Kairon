package com.kairon.identity.app;

import com.kairon.identity.api.UserAccountView;
import com.kairon.identity.domain.AppUser;

/** Maps the {@code AppUser} entity to the module's public {@link UserAccountView}. */
final class AccountViews {

    private AccountViews() {
    }

    static UserAccountView of(AppUser user) {
        return new UserAccountView(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimezone());
    }
}
