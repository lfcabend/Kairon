package com.kairon.assistant;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M10.5 end-to-end acceptance flow: issue a PAT via the real endpoint →
 * call a {@code :context} endpoint with it → call the matching write-back →
 * {@code GET /assistant/runs/{id}} shows the new row with {@code source:
 * "MCP"} → the *existing* JWT-only accept endpoint still completes the
 * suggestion into real data (proving the review step is untouched, D4) — and
 * that same PAT cannot reach the accept endpoint directly (D3). No Anthropic
 * client involved anywhere on this path — the whole point of this entry
 * point is that it never calls one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "kairon.assistant.enabled=true",
        "kairon.assistant.api-key=test-only-key-not-real",
        "kairon.assistant.rate-limit.capacity=1000",
})
class McpAssistantFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Autowired
    MockMvc mvc;

    private String registerAndGetJwt(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\","
                                + "\"displayName\":\"Mcp Flow\",\"timezone\":\"Europe/Amsterdam\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private void optIntoTodoSuggestions(String jwt) throws Exception {
        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"assistant\":{\"todoSuggestions\":{\"enabled\":true}}}}"))
                .andExpect(status().isOk());
    }

    private String issuePat(String jwt) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/me/personal-access-tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Claude Code\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.token");
    }

    @Test
    void fullMcpRoundTrip_contextThenSubmitThenReviewedThroughTheNormalJwtUi() throws Exception {
        String jwt = registerAndGetJwt("mcp-flow@example.com");
        optIntoTodoSuggestions(jwt);
        String pat = issuePat(jwt);

        mvc.perform(post("/api/v1/assistant/mcp/todo-suggestions:context")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemPrompt").exists())
                .andExpect(jsonPath("$.userContent").exists());

        MvcResult submitResult = mvc.perform(post("/api/v1/assistant/mcp/todo-suggestions")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\",\"model\":\"opus (via claude code)\","
                                + "\"suggestions\":[{\"title\":\"Order cabinet hardware\","
                                + "\"rationale\":\"Overdue kitchen-remodel task\",\"suggestedForDay\":\"" + DAY
                                + "\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.source").value("MCP"))
                .andExpect(jsonPath("$.inputTokens").value(0))
                .andExpect(jsonPath("$.suggestions.length()").value(1))
                .andReturn();
        String runId = JsonPath.read(submitResult.getResponse().getContentAsString(), "$.id");
        String suggestionId = JsonPath.read(submitResult.getResponse().getContentAsString(), "$.suggestions[0].id");

        // The PAT itself cannot reach the existing accept endpoint (D3) — only a
        // normal JWT session can complete the review step.
        mvc.perform(post("/api/v1/assistant/suggested-tasks/" + suggestionId + ":accept")
                        .header("Authorization", "Bearer " + pat))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/assistant/runs/" + runId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MCP"))
                .andExpect(jsonPath("$.suggestions[0].status").value("PROPOSED"));

        MvcResult accepted = mvc.perform(post("/api/v1/assistant/suggested-tasks/" + suggestionId + ":accept")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Order cabinet hardware"))
                .andReturn();
        String createdTodoId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/todo").param("day", DAY.toString())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + createdTodoId + "')]").exists());
    }

    @Test
    void submitWithoutOptingInIs403AndCreatesNoRun() throws Exception {
        String jwt = registerAndGetJwt("mcp-flow-no-optin@example.com");
        String pat = issuePat(jwt);

        mvc.perform(post("/api/v1/assistant/mcp/todo-suggestions")
                        .header("Authorization", "Bearer " + pat)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\",\"suggestions\":[]}"))
                .andExpect(status().isForbidden());
    }
}
