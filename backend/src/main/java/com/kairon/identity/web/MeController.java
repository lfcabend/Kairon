package com.kairon.identity.web;

import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.identity.app.UserProfileService;
import com.kairon.identity.app.UserProfileService.UpdateProfileCommand;
import com.kairon.identity.web.MeDtos.MeResponse;
import com.kairon.identity.web.MeDtos.UpdateMeRequest;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/me} — the authenticated user's own profile. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserProfileService profiles;

    public MeController(UserProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public MeResponse me(@CurrentUser UserId userId) {
        return MeResponse.from(profiles.get(userId));
    }

    @PatchMapping
    public MeResponse update(@CurrentUser UserId userId, @Valid @RequestBody UpdateMeRequest request) {
        return MeResponse.from(profiles.update(userId,
                new UpdateProfileCommand(request.displayName(), request.timezone(), request.preferences())));
    }
}
