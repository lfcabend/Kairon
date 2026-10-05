package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.BatchHandle;
import com.kairon.assistant.llm.AnthropicClient.BatchRequestItem;
import com.kairon.assistant.repo.AssistantBatchRepository;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.common.security.UserId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryBatchDispatcherTest {

    private static final UUID USER_ID = UUID.fromString("018f5b3e-0000-7000-8000-0000000000e1");
    private static final LocalDate START = LocalDate.of(2026, 9, 21);
    private static final LocalDate END = LocalDate.of(2026, 9, 27);

    @Mock
    AssistantRunRepository runs;

    @Mock
    AssistantBatchRepository batches;

    @Mock
    SummaryContextBuilder contextBuilder;

    @Mock
    AnthropicClient anthropicClient;

    private SummaryBatchDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SummaryBatchDispatcher(runs, batches, contextBuilder, anthropicClient);
    }

    private AssistantRun run() {
        return AssistantRun.pending(USER_ID, AssistantRunKind.WEEKLY_SUMMARY, START, END, "claude-sonnet-5");
    }

    private SummaryContextBuilder.Context context() {
        return new SummaryContextBuilder.Context("system", "user content", new java.util.HashMap<>(),
                new com.kairon.todo.api.TodoApi.PeriodStats(1, 1, 0), List.of(), List.of(), List.of());
    }

    @Test
    void submitBatch_withNoRunIds_submitsNothing() {
        dispatcher.submitBatch(AssistantRunKind.WEEKLY_SUMMARY, List.of());

        verify(anthropicClient, never()).submitBatch(any());
        verify(batches, never()).save(any());
    }

    @Test
    void submitBatch_buildsOneRequestPerRunAndStoresTheRenderedStatsTableBeforeSubmission() {
        AssistantRun run1 = run();
        AssistantRun run2 = run();
        when(runs.findById(run1.getId())).thenReturn(Optional.of(run1));
        when(runs.findById(run2.getId())).thenReturn(Optional.of(run2));
        when(contextBuilder.build(any(), any(), any(), any())).thenReturn(context());
        when(anthropicClient.submitBatch(any())).thenReturn(new BatchHandle("batch-123"));

        dispatcher.submitBatch(AssistantRunKind.WEEKLY_SUMMARY, List.of(run1.getId(), run2.getId()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BatchRequestItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(anthropicClient).submitBatch(itemsCaptor.capture());
        List<BatchRequestItem> items = itemsCaptor.getValue();
        assertThat(items).hasSize(2);
        assertThat(items).extracting(BatchRequestItem::customId)
                .containsExactly(run1.getId().toString(), run2.getId().toString());

        // D23 — the rendered stats table is stored at submission time, not left to be
        // recomputed at poll time.
        assertThat(run1.getInputSnapshot()).containsKey("statsTable");
        assertThat(run2.getInputSnapshot()).containsKey("statsTable");
    }

    @Test
    void submitBatch_setsBatchIdOnEveryStagedRun() {
        AssistantRun run1 = run();
        when(runs.findById(run1.getId())).thenReturn(Optional.of(run1));
        when(contextBuilder.build(any(), any(), any(), any())).thenReturn(context());
        when(anthropicClient.submitBatch(any())).thenReturn(new BatchHandle("batch-456"));

        dispatcher.submitBatch(AssistantRunKind.WEEKLY_SUMMARY, List.of(run1.getId()));

        assertThat(run1.getBatchId()).isNotNull();
        verify(batches, times(1)).save(any());
        verify(runs, times(1)).save(run1);
    }
}
