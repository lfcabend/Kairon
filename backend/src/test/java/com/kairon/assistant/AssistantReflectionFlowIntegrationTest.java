package com.kairon.assistant;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.ReflectionResult;

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
 * The M10 acceptance flow (docs/milestones/M10-journal-reflection.md): a user
 * not opted in gets 403; a week with no journal entries gets 422 and creates
 * no run (D8); opting in and seeding a journal entry then requesting a
 * reflection returns PENDING immediately and {@code @Async} generation
 * completes it with the model's narrative markdown (no appended table, D11);
 * an in-flight reflection run blocks a second reflection request (409) but
 * never blocks, nor is blocked by, a concurrent summary request (D5).
 * {@link AnthropicClient} is faked via {@code @MockitoBean} — no real network
 * call ever happens in CI.
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
class AssistantReflectionFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

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

    private void optIntoJournalReflection(String token) throws Exception {
        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"assistant\":{\"journalReflection\":{\"enabled\":true}}}}"))
                .andExpect(status().isOk());
    }

    // PATCH /me fully replaces `preferences` (no server-side deep merge — the
    // read-modify-write happens client-side in the real app), so both opt-ins
    // this test needs must be written in a single call.
    private void optIntoJournalReflectionAndExecutionSummaries(String token) throws Exception {
        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"assistant\":{\"journalReflection\":{\"enabled\":true},"
                                + "\"executionSummaries\":{\"enabled\":true}}}}"))
                .andExpect(status().isOk());
    }

    private void seedJournalEntry(String token) throws Exception {
        mvc.perform(post("/api/v1/journal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + TODAY + "\",\"content\":\"Felt good about the week.\",\"mood\":4}"))
                .andExpect(status().isCreated());
    }

    @Test
    void requestingAReflectionWithoutOptingInIs403() throws Exception {
        String token = register("reflection-flow-no-optin@example.com");

        mvc.perform(post("/api/v1/assistant/journal-reflection")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekOf\":\"" + TODAY + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestingAReflectionForAnEmptyWeekIs422AndCreatesNoRun() throws Exception {
        String token = register("reflection-flow-empty-week@example.com");
        optIntoJournalReflection(token);

        mvc.perform(post("/api/v1/assistant/journal-reflection")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekOf\":\"" + TODAY + "\"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/v1/assistant/runs?kind=JOURNAL_REFLECTION")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void fullFlowRequestThenPollUntilSucceeded_andASecondConcurrentReflectionIs409_butASummaryStillSucceeds()
            throws Exception {
        String token = register("reflection-flow@example.com");
        optIntoJournalReflectionAndExecutionSummaries(token);
        seedJournalEntry(token);

        CountDownLatch releaseAnthropicCall = new CountDownLatch(1);
        when(anthropicClient.generateReflection(any())).thenAnswer(inv -> {
            releaseAnthropicCall.await(5, TimeUnit.SECONDS);
            return new ReflectionResult("### Patterns\n\nA canned test reflection.", "claude-sonnet-5", 700, 240);
        });
        when(anthropicClient.generateSummary(any())).thenReturn(
                new com.kairon.assistant.llm.AnthropicClient.SummaryResult(
                        "## This week: steady\n\nA canned test narrative.", "claude-sonnet-5", 500, 200));

        MvcResult first = mvc.perform(post("/api/v1/assistant/journal-reflection")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekOf\":\"" + TODAY + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.periodStart").value(WEEK_START.toString()))
                .andReturn();
        String runId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        // The first reflection run is still PENDING/RUNNING (blocked on the latch) —
        // a second concurrent reflection request must be rejected (D5)...
        mvc.perform(post("/api/v1/assistant/journal-reflection")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekOf\":\"" + TODAY + "\"}"))
                .andExpect(status().isConflict());

        // ...but a concurrent summary request is a different kind and must still succeed.
        mvc.perform(post("/api/v1/assistant/summaries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"WEEK\",\"date\":\"" + TODAY + "\"}"))
                .andExpect(status().isCreated());

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
        assertThat(outputMarkdown).isEqualTo("### Patterns\n\nA canned test reflection.");
        // D11 — no appended stats table, unlike a WEEKLY_SUMMARY run's output.
        assertThat(outputMarkdown).doesNotContain("## Stats");
        assertThat((Integer) JsonPath.read(body, "$.inputTokens")).isEqualTo(700);
        assertThat((Integer) JsonPath.read(body, "$.outputTokens")).isEqualTo(240);

        mvc.perform(get("/api/v1/assistant/runs?kind=JOURNAL_REFLECTION")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUCCEEDED"));
    }
}
