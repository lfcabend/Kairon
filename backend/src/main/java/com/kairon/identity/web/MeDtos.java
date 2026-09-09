package com.kairon.identity.web;

import java.util.Map;
import java.util.UUID;

import com.kairon.identity.app.UserProfileService.ProfileView;

import jakarta.validation.constraints.Size;

/** Request/response bodies for {@code /api/v1/me}. */
final class MeDtos {

    private MeDtos() {
    }

    record MeResponse(
            UUID id,
            String email,
            String displayName,
            String timezone,
            String status,
            Map<String, Object> preferences) {

        static MeResponse from(ProfileView view) {
            return new MeResponse(view.id(), view.email(), view.displayName(), view.timezone(),
                    view.status(), view.preferences());
        }
    }

    /** All fields optional; a null field leaves that attribute unchanged. */
    record UpdateMeRequest(
            @Size(max = 80) String displayName,
            @Size(max = 64) String timezone,
            Map<String, Object> preferences) {
    }
}
