package com.kairon.assistant.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.AssistantRunService;
import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.app.AssistantSuggestedProjectView;
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

@WebMvcTest(ProjectPlanController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class ProjectPlanControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001301");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

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
        AssistantSuggestedProjectView suggestedProject = new AssistantSuggestedProjectView(UUID.randomUUID(), id,
                "PROPOSED", "Kitchen remodel", "desc", "M", DAY, DAY.plusDays(30), List.of(), List.of(), null);
        return new AssistantRunView(id, "PROJECT_GENERATION", "SUCCEEDED", "claude-sonnet-5", DAY, DAY.plusDays(30),
                640, 890, null, Instant.parse("2026-09-25T08:00:00Z"), List.of(), suggestedProject);
    }

    @Test
    void generateReturns201WithTheRunAndItsPlan() throws Exception {
        UUID runId = UUID.randomUUID();
        when(assistantRuns.requestProjectPlan(any(), eq("Kitchen remodel"), eq(DAY), any())).thenReturn(run(runId));

        mvc.perform(post("/api/v1/assistant/project-plan").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + DAY + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.suggestedProject.name").value("Kitchen remodel"));
    }

    @Test
    void generateWhenDisabledSurfacesThe403FromTheService() throws Exception {
        when(assistantRuns.requestProjectPlan(any(), eq("Kitchen remodel"), eq(DAY), any()))
                .thenThrow(ApiException.forbidden("You haven't enabled project generation in Settings."));

        mvc.perform(post("/api/v1/assistant/project-plan").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + DAY + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void generateWithoutADescriptionIs400() throws Exception {
        mvc.perform(post("/api/v1/assistant/project-plan").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"" + DAY + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateWithoutAStartDateIs400() throws Exception {
        mvc.perform(post("/api/v1/assistant/project-plan").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(post("/api/v1/assistant/project-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + DAY + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
