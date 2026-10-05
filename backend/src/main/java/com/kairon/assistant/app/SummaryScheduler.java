package com.kairon.assistant.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.UserAccountApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Two cron triggers (weekly Monday AM, monthly on the 1st, both UTC by
 * default) that auto-generate execution summaries for every user opted into
 * {@code executionSummaries}. Resolves "yesterday" ({@code
 * LocalDate.now(clock).minusDays(1)}) so a Monday firing summarizes the week
 * that just ended and a 1st-of-month firing summarizes the month that just
 * ended (docs/milestones/M9-execution-summaries.md D8). Every per-user
 * failure is caught and logged; the sweep always reaches the next user (D10).
 *
 * <p>Since M10, this no longer dispatches each user's run individually
 * ({@code @Async}): it queues every opted-in user's {@code PENDING} run first
 * (unchanged), then hands the whole firing's run ids to
 * {@link SummaryBatchDispatcher} as **one** Anthropic Message Batch
 * (docs/milestones/M10-journal-reflection.md D17/D20) — on-demand
 * {@code POST /assistant/summaries} is untouched and still dispatches via
 * {@link SummaryGenerationService} per request.
 */
@Component
class SummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SummaryScheduler.class);

    private final AssistantRunService assistantRuns;
    private final SummaryBatchDispatcher batchDispatcher;
    private final UserAccountApi accounts;
    private final AssistantProperties properties;
    private final Clock clock;

    SummaryScheduler(AssistantRunService assistantRuns, SummaryBatchDispatcher batchDispatcher,
            UserAccountApi accounts, AssistantProperties properties, Clock clock) {
        this.assistantRuns = assistantRuns;
        this.batchDispatcher = batchDispatcher;
        this.accounts = accounts;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${kairon.assistant.summary.weekly-cron:0 0 7 * * MON}",
            zone = "${kairon.assistant.summary.zone:UTC}")
    void weekly() {
        run(SummaryPeriod.WEEK);
    }

    @Scheduled(cron = "${kairon.assistant.summary.monthly-cron:0 0 7 1 * *}",
            zone = "${kairon.assistant.summary.zone:UTC}")
    void monthly() {
        run(SummaryPeriod.MONTH);
    }

    private void run(SummaryPeriod period) {
        if (!properties.available()) {
            log.debug("Skipping {} auto-summary sweep — assistant module not available instance-wide", period);
            return;
        }
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        List<UUID> runIds = new ArrayList<>();
        int skipped = 0;
        for (UserId userId : accounts.usersOptedIntoExecutionSummaries()) {
            try {
                AssistantRunView view = assistantRuns.requestSummary(userId, period, yesterday);
                runIds.add(view.id());
            } catch (ApiException e) {
                log.info("Skipped {} auto-summary userId={}: {}", period, userId.value(), e.getMessage());
                skipped++;
            } catch (Exception e) {
                log.warn("Auto-summary generation failed to queue userId={}", userId.value(), e);
                skipped++;
            }
        }
        batchDispatcher.submitBatch(period.kind(), runIds);
        log.info("{} auto-summary sweep complete: queued={} skipped={}", period, runIds.size(), skipped);
    }
}
