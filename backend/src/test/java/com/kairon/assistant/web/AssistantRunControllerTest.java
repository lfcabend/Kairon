package com.kairon.assistant.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.AssistantRunService;
import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.app.AssistantSuggestedTaskView;
import com.kairon.assistant.app.Horizon;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssistantRunController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class AssistantRunControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001301");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

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
        AssistantSuggestedTaskView suggestion = new AssistantSuggestedTaskView(UUID.randomUUID(), id,
                "Order cabinet hardware", null, "Overdue kitchen-remodel task", DAY, 20, null,
                "PROPOSED", null, 0);
        return new AssistantRunView(id, "TODO_SUGGESTION", "SUCCEEDED", "claude-sonnet-5", DAY, DAY,
                1840, 310, null, Instant.parse("2026-09-21T08:00:00Z"), List.of(suggestion), null);
    }

    @Test
    void suggestTodosReturns201WithTheRunAndItsSuggestions() throws Exception {
        UUID runId = UUID.randomUUID();
        when(assistantRuns.requestTodoSuggestions(any(), eq(DAY), eq(Horizon.DAY))).thenReturn(run(runId));

        mvc.perform(post("/api/v1/assistant/todo-suggestions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(runId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.suggestions[0].title").value("Order cabinet hardware"));
    }

    @Test
    void suggestTodosWhenDisabledSurfacesThe403FromTheService() throws Exception {
        when(assistantRuns.requestTodoSuggestions(any(), eq(DAY), eq(Horizon.DAY)))
                .thenThrow(ApiException.forbidden("Assistant features are not enabled on this instance."));

        mvc.perform(post("/api/v1/assistant/todo-suggestions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void suggestTodosWithoutADayIs400() throws Exception {
        mvc.perform(post("/api/v1/assistant/todo-suggestions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizon\":\"DAY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRunReturnsTheStoredRun() throws Exception {
        UUID runId = UUID.randomUUID();
        when(assistantRuns.get(any(), eq(runId))).thenReturn(run(runId));

        mvc.perform(get("/api/v1/assistant/runs/" + runId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId.toString()));
    }

    @Test
    void getMissingOrForeignRunIs404() throws Exception {
        UUID runId = UUID.randomUUID();
        when(assistantRuns.get(any(), eq(runId))).thenThrow(ApiException.notFound("Assistant run not found."));

        mvc.perform(get("/api/v1/assistant/runs/" + runId).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRunReturns204() throws Exception {
        UUID runId = UUID.randomUUID();

        mvc.perform(delete("/api/v1/assistant/runs/" + runId).with(asUser()))
                .andExpect(status().isNoContent());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/assistant/runs/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
