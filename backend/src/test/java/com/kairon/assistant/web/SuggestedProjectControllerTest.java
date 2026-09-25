package com.kairon.assistant.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.AssistantSuggestedProjectView;
import com.kairon.assistant.app.SuggestedProjectService;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.projects.api.ProjectView;

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

@WebMvcTest(SuggestedProjectController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class SuggestedProjectControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001301");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SuggestedProjectService suggestedProjects;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    @Test
    void acceptReturns201WithTheCreatedProject() throws Exception {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectView created = new ProjectView(projectId, null, "Kitchen remodel", null, "PLANNING", null, 100,
                "#6366f1", DAY, DAY.plusDays(30), null, null, Instant.now(), Instant.now(), 0);
        when(suggestedProjects.accept(any(), eq(id), eq(List.of("t2")))).thenReturn(created);

        mvc.perform(post("/api/v1/assistant/suggested-projects/" + id + ":accept").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"excludedTaskKeys\":[\"t2\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(projectId.toString()));
    }

    @Test
    void acceptWithNoBodyDefaultsToNoExclusions() throws Exception {
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ProjectView created = new ProjectView(projectId, null, "Kitchen remodel", null, "PLANNING", null, 100,
                "#6366f1", DAY, DAY.plusDays(30), null, null, Instant.now(), Instant.now(), 0);
        when(suggestedProjects.accept(any(), eq(id), eq(List.of()))).thenReturn(created);

        mvc.perform(post("/api/v1/assistant/suggested-projects/" + id + ":accept").with(asUser()))
                .andExpect(status().isCreated());
    }

    @Test
    void acceptOnAnAlreadyResolvedPlanIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(suggestedProjects.accept(any(), eq(id), eq(List.of())))
                .thenThrow(ApiException.conflict("This project plan was already accepted."));

        mvc.perform(post("/api/v1/assistant/suggested-projects/" + id + ":accept").with(asUser()))
                .andExpect(status().isConflict());
    }

    @Test
    void dismissReturns200WithTheDismissedPlan() throws Exception {
        UUID id = UUID.randomUUID();
        AssistantSuggestedProjectView view = new AssistantSuggestedProjectView(id, UUID.randomUUID(), "DISMISSED",
                "Kitchen remodel", "desc", "M", DAY, DAY.plusDays(30), List.of(), List.of(), null);
        when(suggestedProjects.dismiss(any(), eq(id))).thenReturn(view);

        mvc.perform(post("/api/v1/assistant/suggested-projects/" + id + ":dismiss").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void missingOrForeignPlanIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(suggestedProjects.dismiss(any(), eq(id)))
                .thenThrow(ApiException.notFound("Suggested project not found."));

        mvc.perform(post("/api/v1/assistant/suggested-projects/" + id + ":dismiss").with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/assistant/suggested-projects/" + id + ":dismiss"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
