package com.kairon.assistant;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.SummaryResult;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M9 acceptance flow (docs/milestones/M9-execution-summaries.md): a user
 * not opted in gets 403; requesting a summary returns PENDING immediately and
 * the {@code @Async} generation completes it with a narrative plus the
 * deterministic stats table; a second concurrent request while one is in
 * flight gets 409 (D3). {@link AnthropicClient} is faked via
 * {@code @MockitoBean} — no real network call ever happens in CI.
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
class AssistantSummaryFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    // "Today" for the test run, not a fixed historical date — todo/task
    // completion timestamps are set by the app's real Clock.systemUTC() bean,
    // so the summary period requested must be the week actually containing "now".
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalDate WEEK_START = TODAY.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

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

    private void optIntoExecutionSummaries(String token) throws Exception {
        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"assistant\":{\"executionSummaries\":{\"enabled\":true}}}}"))
                .andExpect(status().isOk());
    }

    private void seedCompletedTodo(String token) throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/todo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + TODAY + "\",\"title\":\"Order cabinet hardware\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String todoId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/todo/" + todoId + ":complete").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String seedCompletedProjectTask(String token) throws Exception {
        MvcResult project = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kitchen remodel\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = JsonPath.read(project.getResponse().getContentAsString(), "$.id");

        MvcResult task = mvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Order hardware\",\"estimateHours\":2.0}"))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = JsonPath.read(task.getResponse().getContentAsString(), "$.id");

        mvc.perform(patch("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Order hardware\",\"status\":\"DONE\",\"progressPercent\":100,"
                                + "\"isMilestone\":false,\"estimateHours\":2.0,\"actualHours\":3.0}"))
                .andExpect(status().isOk());
        return projectId;
    }

    @Test
    void requestingASummaryWithoutOptingInIs403() throws Exception {
        String token = register("summary-flow-no-optin@example.com");

        mvc.perform(post("/api/v1/assistant/summaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"WEEK\",\"date\":\"" + TODAY + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullFlowRequestThenPollUntilSucceeded_andASecondConcurrentRequestIs409() throws Exception {
        String token = register("summary-flow@example.com");
        optIntoExecutionSummaries(token);
        seedCompletedTodo(token);
        seedCompletedProjectTask(token);

        CountDownLatch releaseAnthropicCall = new CountDownLatch(1);
        when(anthropicClient.generateSummary(any())).thenAnswer(inv -> {
            releaseAnthropicCall.await(5, TimeUnit.SECONDS);
            return new SummaryResult("## This week: steady progress\n\nA canned test narrative.",
                    "claude-sonnet-5", 500, 200);
        });

        MvcResult first = mvc.perform(post("/api/v1/assistant/summaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"WEEK\",\"date\":\"" + TODAY + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.periodStart").value(WEEK_START.toString()))
                .andReturn();
        String runId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        // The first run is still PENDING/RUNNING (blocked on the latch) — a second
        // concurrent request must be rejected, not queue a second billed call (D3).
        mvc.perform(post("/api/v1/assistant/summaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"WEEK\",\"date\":\"" + TODAY + "\"}"))
                .andExpect(status().isConflict());

        releaseAnthropicCall.countDown();

        String runStatus = "PENDING";
        String body = null;
        for (int i = 0; i < 50 && ("PENDING".equals(runStatus) || "RUNNING".equals(runStatus)); i++) {
            Thread.sleep(100);
            MvcResult poll = mvc.perform(get("/api/v1/assistant/runs/" + runId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            body = poll.getResponse().getContentAsString();
            runStatus = JsonPath.read(body, "$.status");
        }

        assertThat(runStatus).isEqualTo("SUCCEEDED");
        String outputMarkdown = JsonPath.read(body, "$.outputMarkdown");
        assertThat(outputMarkdown).contains("A canned test narrative.");
        assertThat(outputMarkdown).contains("## Stats");
        assertThat(outputMarkdown).contains("| Todos completed | 1 |");
        assertThat(outputMarkdown).contains("Kitchen remodel");
        assertThat((Integer) JsonPath.read(body, "$.inputTokens")).isEqualTo(500);
        assertThat((Integer) JsonPath.read(body, "$.outputTokens")).isEqualTo(200);

        // Now that the run has finished, the history list should include it.
        mvc.perform(get("/api/v1/assistant/runs?kind=WEEKLY_SUMMARY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUCCEEDED"));
    }
}
