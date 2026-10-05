package com.kairon.assistant.app;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.BatchHandle;
import com.kairon.assistant.llm.AnthropicClient.BatchPollResult;
import com.kairon.assistant.llm.AnthropicClient.BatchRequestItem;
import com.kairon.assistant.llm.AnthropicClient.BatchResultItem;

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
 * The M10 batch-migration acceptance flow (docs/milestones/
 * M10-journal-reflection.md D17-D24): a scheduled sweep for two opted-in
 * users submits their requests as **one** Anthropic batch (not two individual
 * calls), and a single {@link SummaryBatchPollingScheduler#poll()} tick —
 * once the fake batch reports {@code ENDED} — writes both users' runs back to
 * {@code SUCCEEDED} with their stats table intact. Deliberately placed in
 * {@code com.kairon.assistant.app} (unlike every other {@code @SpringBootTest}
 * flow test, which lives in the top-level {@code com.kairon.assistant}
 * package) so it can autowire the package-private {@link SummaryScheduler}/
 * {@link SummaryBatchPollingScheduler} beans directly and drive a firing
 * without waiting on either's real cron/fixed-delay trigger.
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
class AssistantBatchFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalDate WEEK_START =
            TODAY.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

    @Autowired
    MockMvc mvc;

    @Autowired
    SummaryScheduler scheduler;

    @Autowired
    SummaryBatchPollingScheduler poller;

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

    @Test
    void scheduledSweepForTwoOptedInUsers_submitsOneBatchAndBothRunsSucceedAfterOnePollTick() throws Exception {
        String tokenA = register("batch-flow-a@example.com");
        String tokenB = register("batch-flow-b@example.com");
        optIntoExecutionSummaries(tokenA);
        optIntoExecutionSummaries(tokenB);

        when(anthropicClient.submitBatch(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<BatchRequestItem> items = inv.getArgument(0);
            assertThat(items).hasSize(2); // one Anthropic batch, not two individual calls (D20)
            return new BatchHandle("fake-anthropic-batch-1");
        });
        when(anthropicClient.pollBatch("fake-anthropic-batch-1")).thenReturn(new BatchPollResult(true, "ENDED"));

        // Drive the sweep directly rather than waiting on SummaryScheduler's real cron
        // trigger — this test only cares about the batch dispatch/poll mechanism.
        scheduler.weekly();

        // Capture the two run ids the sweep actually queued, from each user's own history.
        MvcResult runsA = mvc.perform(get("/api/v1/assistant/runs?kind=WEEKLY_SUMMARY")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("RUNNING"))
                .andReturn();
        String runIdA = JsonPath.read(runsA.getResponse().getContentAsString(), "$.content[0].id");
        MvcResult runsB = mvc.perform(get("/api/v1/assistant/runs?kind=WEEKLY_SUMMARY")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("RUNNING"))
                .andReturn();
        String runIdB = JsonPath.read(runsB.getResponse().getContentAsString(), "$.content[0].id");

        when(anthropicClient.retrieveBatchResults("fake-anthropic-batch-1")).thenReturn(List.of(
                new BatchResultItem(runIdA, true, "## User A's week\n\nA finished narrative.", 500, 200, null),
                new BatchResultItem(runIdB, true, "## User B's week\n\nAnother finished narrative.", 400, 150, null)));

        poller.poll();

        MvcResult afterA = mvc.perform(get("/api/v1/assistant/runs/" + runIdA)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        String bodyA = afterA.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(bodyA, "$.status")).isEqualTo("SUCCEEDED");
        String outputA = JsonPath.read(bodyA, "$.outputMarkdown");
        assertThat(outputA).contains("A finished narrative.");
        assertThat(outputA).contains("## Stats"); // D23 — the stats table rendered at submission time, intact
        assertThat((Integer) JsonPath.read(bodyA, "$.inputTokens")).isEqualTo(500);

        MvcResult afterB = mvc.perform(get("/api/v1/assistant/runs/" + runIdB)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        String bodyB = afterB.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(bodyB, "$.status")).isEqualTo("SUCCEEDED");
        assertThat((String) JsonPath.read(bodyB, "$.outputMarkdown")).contains("Another finished narrative.");

        // The poll also flips the batch itself to ENDED — a second tick finds nothing left to do.
        poller.poll();
    }
}
