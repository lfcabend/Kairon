package com.kairon.identity.web;

import java.util.Map;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.identity.app.UserProfileService;
import com.kairon.identity.app.UserProfileService.ProfileView;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class MeControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserProfileService profiles;

    @MockitoBean
    JwtDecoder jwtDecoder; // satisfies the resource-server auto-config in the slice

    @Test
    void getMeReturnsTheProfileOfTheTokenSubject() throws Exception {
        when(profiles.get(any())).thenReturn(new ProfileView(USER, "ada@example.com", "Ada", "UTC",
                "ACTIVE", Map.of("theme", "dark")));

        mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject(USER.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.preferences.theme").value("dark"));
    }

    @Test
    void getMeWithoutATokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void patchMeWithAnUnknownTimezoneIs400() throws Exception {
        when(profiles.update(any(), any()))
                .thenThrow(ApiException.badRequest("Unknown timezone: Mars/Olympus"));

        mvc.perform(patch("/api/v1/me")
                        .with(jwt().jwt(j -> j.subject(USER.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Mars/Olympus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown timezone: Mars/Olympus"));
    }

    @Test
    void patchMeUpdatesTheDisplayName() throws Exception {
        when(profiles.update(eq(com.kairon.common.security.UserId.of(USER)), any()))
                .thenReturn(new ProfileView(USER, "ada@example.com", "Ada Lovelace", "UTC",
                        "ACTIVE", Map.of()));

        mvc.perform(patch("/api/v1/me")
                        .with(jwt().jwt(j -> j.subject(USER.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada Lovelace\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));
    }
}
