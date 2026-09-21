package com.kairon.assistant;

import java.time.LocalDate;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.TodoSuggestionsResult;
import com.kairon.assistant.llm.SuggestedTaskPayload;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M8 acceptance flow (docs/milestones/M8-assistant-foundations.md): a
 * user not opted in gets 403; opting in and requesting suggestions persists a
 * SUCCEEDED run with its suggestions; accepting one creates a linked todo
 * visible on {@code GET /todo?day=}; dismissing another just flips its
 * status; a foreign suggested-task id 404s. {@link AnthropicClient} is faked
 * via {@code @MockitoBean} — no real network call ever happens in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "kairon.assistant.enabled=true",
        "kairon.assistant.api-key=test-only-key-not-real",
        // Generous so the flow's several assistant calls aren't throttled by the
        // tight production default (5 / 10m) — same pattern application-test.yml
        // already documents for the auth rate limiter.
        "kairon.assistant.rate-limit.capacity=1000",
})
class AssistantFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AnthropicClient anthropicClient;

    private String register(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\","
                                + "\"displayName\":\"Flow\",\"timezone\":\"Europe/Amsterdam\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private void optIntoTodoSuggestions(String token) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"assistant\":{\"todoSuggestions\":{\"enabled\":true}}}}"))
                .andExpect(status().isOk());
    }

    @Test
    void requestingSuggestionsWithoutOptingInIs403() throws Exception {
        String token = register("assistant-flow-no-optin@example.com");

        mvc.perform(post("/api/v1/assistant/todo-suggestions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullFlowSuggestAcceptDismiss() throws Exception {
        String token = register("assistant-flow@example.com");
        optIntoTodoSuggestions(token);

        SuggestedTaskPayload keep = new SuggestedTaskPayload("Order cabinet hardware", null,
                "Overdue kitchen-remodel task", DAY, 20, null);
        SuggestedTaskPayload skip = new SuggestedTaskPayload("Write homepage copy", null,
                "Open but not urgent", DAY, 30, null);
        when(anthropicClient.suggestTodos(any()))
                .thenReturn(new TodoSuggestionsResult(List.of(keep, skip), "claude-sonnet-5", 1200, 240));

        MvcResult runResult = mvc.perform(post("/api/v1/assistant/todo-suggestions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.suggestions.length()").value(2))
                .andReturn();
        String body = runResult.getResponse().getContentAsString();
        String runId = JsonPath.read(body, "$.id");
        String acceptId = JsonPath.read(body, "$.suggestions[0].id");
        String dismissId = JsonPath.read(body, "$.suggestions[1].id");

        mvc.perform(get("/api/v1/assistant/runs/" + runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputTokens").value(1200));

        MvcResult accepted = mvc.perform(post("/api/v1/assistant/suggested-tasks/" + acceptId + ":accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Order cabinet hardware"))
                .andReturn();
        String createdTodoId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/todo?day=" + DAY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + createdTodoId + "')]").exists());

        // Accepting the same suggestion twice is a conflict, not a second todo.
        mvc.perform(post("/api/v1/assistant/suggested-tasks/" + acceptId + ":accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/assistant/suggested-tasks/" + dismissId + ":dismiss")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void anotherUsersSuggestedTaskIs404() throws Exception {
        String ownerToken = register("assistant-flow-owner@example.com");
        optIntoTodoSuggestions(ownerToken);
        when(anthropicClient.suggestTodos(any())).thenReturn(new TodoSuggestionsResult(
                List.of(new SuggestedTaskPayload("Order cabinet hardware", null, "rationale", DAY, 20, null)),
                "claude-sonnet-5", 100, 100));
        MvcResult runResult = mvc.perform(post("/api/v1/assistant/todo-suggestions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String suggestionId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.suggestions[0].id");

        String otherToken = register("assistant-flow-other@example.com");
        mvc.perform(post("/api/v1/assistant/suggested-tasks/" + suggestionId + ":dismiss")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUpstreamFailureFailsTheRunCleanlyWithoutA500() throws Exception {
        String token = register("assistant-flow-failure@example.com");
        optIntoTodoSuggestions(token);
        when(anthropicClient.suggestTodos(any())).thenThrow(
                new com.kairon.assistant.app.AssistantUpstreamException(true,
                        "The assistant is temporarily unavailable.", null));

        mvc.perform(post("/api/v1/assistant/todo-suggestions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    void deleteRunPurgesItAndItsSuggestions() throws Exception {
        String token = register("assistant-flow-delete@example.com");
        optIntoTodoSuggestions(token);
        when(anthropicClient.suggestTodos(any())).thenReturn(new TodoSuggestionsResult(
                List.of(new SuggestedTaskPayload("Order cabinet hardware", null, "rationale", DAY, 20, null)),
                "claude-sonnet-5", 100, 100));
        MvcResult runResult = mvc.perform(post("/api/v1/assistant/todo-suggestions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.id");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/assistant/runs/" + runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/assistant/runs/" + runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
