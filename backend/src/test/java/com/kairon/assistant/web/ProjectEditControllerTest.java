package com.kairon.assistant.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.AssistantRunService;
import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.app.AssistantSuggestedProjectEditView;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectEditController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class ProjectEditControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001301");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AssistantRunService assistantRuns;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    private static AssistantRunView run(UUID id) {
        AssistantSuggestedProjectEditView suggestedProjectEdit = new AssistantSuggestedProjectEditView(
                UUID.randomUUID(), id, "PROPOSED", UUID.randomUUID(), null, List.of(), List.of(), List.of());
        return new AssistantRunView(id, "PROJECT_EDIT", "SUCCEEDED", "claude-sonnet-5", null, null,
                500, 200, null, Instant.parse("2026-09-25T08:00:00Z"), List.of(), null, suggestedProjectEdit, null);
    }

    @Test
    void generateReturns201WithTheRunAndItsDiff() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(assistantRuns.requestProjectEdit(any(), eq(projectId), eq("add an inspection task")))
                .thenReturn(run(runId));

        mvc.perform(post("/api/v1/assistant/project-edits").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"description\":\"add an inspection task\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.suggestedProjectEdit.status").value("PROPOSED"));
    }

    @Test
    void generateWhenDisabledSurfacesThe403FromTheService() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(assistantRuns.requestProjectEdit(any(), eq(projectId), eq("add a task")))
                .thenThrow(ApiException.forbidden("You haven't enabled project editing in Settings."));

        mvc.perform(post("/api/v1/assistant/project-edits").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"description\":\"add a task\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void generateWithoutADescriptionIs400() throws Exception {
        mvc.perform(post("/api/v1/assistant/project-edits").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateWithoutAProjectIdIs400() throws Exception {
        mvc.perform(post("/api/v1/assistant/project-edits").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"add a task\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(post("/api/v1/assistant/project-edits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + UUID.randomUUID() + "\",\"description\":\"add a task\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
