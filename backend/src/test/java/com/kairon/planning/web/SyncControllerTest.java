package com.kairon.planning.web;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.common.sync.ChangeSet;
import com.kairon.planning.app.SyncService;
import com.kairon.planning.app.SyncService.Snapshot;
import com.kairon.planning.app.SyncType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class SyncControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001301");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SyncService sync;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    private static Snapshot emptySnapshot(Instant since) {
        return new Snapshot(since, ChangeSet.empty(), ChangeSet.empty(), ChangeSet.empty(), ChangeSet.empty());
    }

    @Test
    void sinceOmittedDefaultsToEpoch() throws Exception {
        when(sync.sync(any(), eq(Instant.EPOCH), eq(EnumSet.allOf(SyncType.class))))
                .thenReturn(emptySnapshot(Instant.parse("2026-09-18T07:00:00Z")));

        mvc.perform(get("/api/v1/sync").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.since").value("2026-09-18T07:00:00Z"));
    }

    @Test
    void typesOmittedDefaultsToAllFour() throws Exception {
        when(sync.sync(any(), any(), eq(EnumSet.allOf(SyncType.class))))
                .thenReturn(emptySnapshot(Instant.EPOCH));

        mvc.perform(get("/api/v1/sync").with(asUser()))
                .andExpect(status().isOk());
        verify(sync).sync(any(), eq(Instant.EPOCH), eq(EnumSet.allOf(SyncType.class)));
    }

    @Test
    void typesNarrowsToTheRequestedSet() throws Exception {
        when(sync.sync(any(), any(), eq(EnumSet.of(SyncType.TODO))))
                .thenReturn(emptySnapshot(Instant.EPOCH));

        mvc.perform(get("/api/v1/sync?types=todo").with(asUser()))
                .andExpect(status().isOk());
        verify(sync).sync(any(), eq(Instant.EPOCH), eq(EnumSet.of(SyncType.TODO)));
    }

    @Test
    void anUnknownTypeIs400() throws Exception {
        mvc.perform(get("/api/v1/sync?types=bogus").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", "application/problem+json"));
    }
}
