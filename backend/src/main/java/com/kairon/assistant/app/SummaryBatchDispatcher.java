package com.kairon.assistant.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantBatch;
import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.repo.AssistantBatchRepository;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.common.security.UserId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submits one Anthropic Message Batch covering every run a single
 * {@code SummaryScheduler} firing queued — used only by that scheduler, never
 * by on-demand {@code requestSummary} (docs/milestones/
 * M10-journal-reflection.md D17/D19/D20). Builds each run's context exactly
 * like {@link SummaryGenerationService#generate} does today, stores the
 * rendered stats table under {@code input_snapshot["statsTable"]} at
 * submission time (D23 — never recomputed later, so the table and the
 * narrative the model saw describe the same snapshot even hours later at poll
 * time), and tags every staged run with the new {@code assistant_batch} row's
 * id.
 */
@Service
class SummaryBatchDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SummaryBatchDispatcher.class);

    private final AssistantRunRepository runs;
    private final AssistantBatchRepository batches;
    private final SummaryContextBuilder contextBuilder;
    private final AnthropicClient anthropicClient;

    SummaryBatchDispatcher(AssistantRunRepository runs, AssistantBatchRepository batches,
            SummaryContextBuilder contextBuilder, AnthropicClient anthropicClient) {
        this.runs = runs;
        this.batches = batches;
        this.contextBuilder = contextBuilder;
        this.anthropicClient = anthropicClient;
    }

    @Transactional
    void submitBatch(AssistantRunKind kind, List<UUID> runIds) {
        List<AnthropicClient.BatchRequestItem> items = new ArrayList<>();
        List<AssistantRun> staged = new ArrayList<>();
        for (UUID runId : runIds) {
            AssistantRun run = runs.findById(runId).orElseThrow();
            SummaryContextBuilder.Context context = contextBuilder.build(
                    new UserId(run.getUserId()), run.getKind(), run.getPeriodStart(), run.getPeriodEnd());
            Map<String, Object> snapshot = new HashMap<>(context.inputSnapshot());
            snapshot.put("statsTable", context.renderStatsTable()); // D23 — rendered now, read back at poll time
            run.start(snapshot);
            items.add(new AnthropicClient.BatchRequestItem(
                    run.getId().toString(), context.systemPrompt(), context.userContent(), run.getModel()));
            staged.add(run);
        }
        if (items.isEmpty()) {
            log.debug("No {} runs to batch this firing", kind);
            return;
        }

        AnthropicClient.BatchHandle handle = anthropicClient.submitBatch(items);
        AssistantBatch batch = AssistantBatch.pending(handle.anthropicBatchId(), kind);
        batches.save(batch);
        for (AssistantRun run : staged) {
            run.setBatchId(batch.getId());
            runs.save(run);
        }
        log.info("Submitted {} batch {} ({} runs)", kind, handle.anthropicBatchId(), staged.size());
    }
}
