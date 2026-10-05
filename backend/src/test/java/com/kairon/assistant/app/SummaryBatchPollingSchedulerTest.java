package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantBatch;
import com.kairon.assistant.domain.AssistantBatchStatus;
import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.BatchPollResult;
import com.kairon.assistant.llm.AnthropicClient.BatchResultItem;
import com.kairon.assistant.repo.AssistantBatchRepository;
import com.kairon.assistant.repo.AssistantRunRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryBatchPollingSchedulerTest {

    private static final UUID USER_ID = UUID.fromString("018f5b3e-0000-7000-8000-0000000000f2");
    private static final LocalDate START = LocalDate.of(2026, 9, 21);
    private static final LocalDate END = LocalDate.of(2026, 9, 27);

    @Mock
    AssistantBatchRepository batches;

    @Mock
    AssistantRunRepository runs;

    @Mock
    AnthropicClient anthropicClient;

    private SummaryBatchPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SummaryBatchPollingScheduler(batches, runs, anthropicClient);
    }

    private AssistantRun runWithStatsTable(String statsTable) {
        AssistantRun run = AssistantRun.pending(USER_ID, AssistantRunKind.WEEKLY_SUMMARY, START, END,
                "claude-sonnet-5");
        HashMap<String, Object> snapshot = new HashMap<>();
        snapshot.put("statsTable", statsTable);
        run.start(snapshot);
        return run;
    }

    @Test
    void poll_aNonEndedBatch_onlyUpdatesItsStoredStatus() {
        AssistantBatch batch = AssistantBatch.pending("anthropic-batch-1", AssistantRunKind.WEEKLY_SUMMARY);
        when(batches.findByStatusNot(AssistantBatchStatus.ENDED)).thenReturn(List.of(batch));
        when(anthropicClient.pollBatch("anthropic-batch-1")).thenReturn(new BatchPollResult(false, "CANCELING"));

        scheduler.poll();

        assertThat(batch.getStatus()).isEqualTo(AssistantBatchStatus.CANCELING);
        verify(anthropicClient, never()).retrieveBatchResults(any());
        verify(batches).save(batch);
    }

    @Test
    void poll_anEndedBatch_writesSucceededWithTheNarrativePlusTheStoredStatsTableVerbatim() {
        AssistantBatch batch = AssistantBatch.pending("anthropic-batch-2", AssistantRunKind.WEEKLY_SUMMARY);
        AssistantRun run = runWithStatsTable("## Stats\n\n| Todos completed | 3 |");
        when(batches.findByStatusNot(AssistantBatchStatus.ENDED)).thenReturn(List.of(batch));
        when(anthropicClient.pollBatch("anthropic-batch-2")).thenReturn(new BatchPollResult(true, "ENDED"));
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(anthropicClient.retrieveBatchResults("anthropic-batch-2")).thenReturn(List.of(
                new BatchResultItem(run.getId().toString(), true, "A finished narrative.", 500, 200, null)));

        scheduler.poll();

        assertThat(run.getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(run.getOutputMarkdown()).isEqualTo("A finished narrative.\n\n## Stats\n\n| Todos completed | 3 |");
        assertThat(run.getInputTokens()).isEqualTo(500);
        assertThat(run.getOutputTokens()).isEqualTo(200);
        assertThat(batch.getStatus()).isEqualTo(AssistantBatchStatus.ENDED);
    }

    @Test
    void poll_anErroredResultFailsOnlyThatRun_othersInTheSameBatchStillSucceed() {
        AssistantBatch batch = AssistantBatch.pending("anthropic-batch-3", AssistantRunKind.WEEKLY_SUMMARY);
        AssistantRun goodRun = runWithStatsTable("## Stats");
        AssistantRun badRun = runWithStatsTable("## Stats");
        when(batches.findByStatusNot(AssistantBatchStatus.ENDED)).thenReturn(List.of(batch));
        when(anthropicClient.pollBatch("anthropic-batch-3")).thenReturn(new BatchPollResult(true, "ENDED"));
        when(runs.findById(goodRun.getId())).thenReturn(Optional.of(goodRun));
        when(runs.findById(badRun.getId())).thenReturn(Optional.of(badRun));
        when(anthropicClient.retrieveBatchResults("anthropic-batch-3")).thenReturn(List.of(
                new BatchResultItem(goodRun.getId().toString(), true, "Good narrative.", 300, 100, null),
                new BatchResultItem(badRun.getId().toString(), false, null, 0, 0, "The assistant batch result was errored.")));

        scheduler.poll();

        assertThat(goodRun.getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(badRun.getStatus().name()).isEqualTo("FAILED");
        assertThat(badRun.getError()).isEqualTo("The assistant batch result was errored.");
    }

    @Test
    void poll_theBatchRowFlipsToEndedOnlyAfterEveryResultIsWritten() {
        AssistantBatch batch = AssistantBatch.pending("anthropic-batch-4", AssistantRunKind.WEEKLY_SUMMARY);
        AssistantRun run = runWithStatsTable("## Stats");
        when(batches.findByStatusNot(AssistantBatchStatus.ENDED)).thenReturn(List.of(batch));
        when(anthropicClient.pollBatch("anthropic-batch-4")).thenReturn(new BatchPollResult(true, "ENDED"));
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(anthropicClient.retrieveBatchResults("anthropic-batch-4")).thenReturn(List.of(
                new BatchResultItem(run.getId().toString(), true, "Narrative.", 100, 50, null)));

        scheduler.poll();

        assertThat(batch.getStatus()).isEqualTo(AssistantBatchStatus.ENDED);
        assertThat(batch.getEndedAt()).isNotNull();
        verify(runs).save(run);
        verify(batches).save(batch);
    }

    @Test
    void poll_resultForAnUnknownRunIsSkippedWithoutFailing() {
        AssistantBatch batch = AssistantBatch.pending("anthropic-batch-5", AssistantRunKind.WEEKLY_SUMMARY);
        when(batches.findByStatusNot(AssistantBatchStatus.ENDED)).thenReturn(List.of(batch));
        when(anthropicClient.pollBatch("anthropic-batch-5")).thenReturn(new BatchPollResult(true, "ENDED"));
        UUID unknownRunId = UUID.randomUUID();
        when(runs.findById(unknownRunId)).thenReturn(Optional.empty());
        when(anthropicClient.retrieveBatchResults("anthropic-batch-5")).thenReturn(List.of(
                new BatchResultItem(unknownRunId.toString(), true, "Narrative.", 100, 50, null)));

        scheduler.poll();

        assertThat(batch.getStatus()).isEqualTo(AssistantBatchStatus.ENDED);
        verify(runs, never()).save(any());
    }
}
