package com.kairon.assistant.app;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.UserAccountApi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummarySchedulerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-28T07:00:00Z"), ZoneOffset.UTC);
    private static final UserId USER_A = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000a1"));
    private static final UserId USER_B = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000a2"));

    @Mock
    AssistantRunService assistantRuns;

    @Mock
    SummaryBatchDispatcher batchDispatcher;

    @Mock
    UserAccountApi accounts;

    private AssistantProperties available;

    @BeforeEach
    void setUp() {
        available = new AssistantProperties(true, "sk-test-key", "claude-sonnet-5", null, 0, null, null, null, null,
                null);
    }

    private SummaryScheduler scheduler(AssistantProperties properties) {
        return new SummaryScheduler(assistantRuns, batchDispatcher, accounts, properties, CLOCK);
    }

    @Test
    void weekly_whenInstanceUnavailable_neverQueriesUsers() {
        AssistantProperties disabled = new AssistantProperties(false, "sk-test-key", null, null, 0, null, null, null,
                null, null);

        scheduler(disabled).weekly();

        verify(accounts, never()).usersOptedIntoExecutionSummaries();
        verify(assistantRuns, never()).requestSummary(any(), any(), any());
        verify(batchDispatcher, never()).submitBatch(any(), any());
    }

    @Test
    void weekly_withNoOptedInUsers_submitsAnEmptyBatch() {
        when(accounts.usersOptedIntoExecutionSummaries()).thenReturn(List.of());

        scheduler(available).weekly();

        verify(assistantRuns, never()).requestSummary(any(), any(), any());
        verify(batchDispatcher).submitBatch(AssistantRunKind.WEEKLY_SUMMARY, List.of());
    }

    @Test
    void weekly_oneUsersForbiddenOrConflictFailureDoesNotStopTheSweep() {
        when(accounts.usersOptedIntoExecutionSummaries()).thenReturn(List.of(USER_A, USER_B));
        when(assistantRuns.requestSummary(eq(USER_A), eq(SummaryPeriod.WEEK), any()))
                .thenThrow(ApiException.forbidden("Monthly assistant usage budget reached."));
        UUID runIdB = UUID.randomUUID();
        when(assistantRuns.requestSummary(eq(USER_B), eq(SummaryPeriod.WEEK), any()))
                .thenReturn(new AssistantRunView(runIdB, "WEEKLY_SUMMARY", "PENDING", "claude-sonnet-5",
                        null, null, null, null, null, Instant.now(), List.of(), null, null, null));

        scheduler(available).weekly();

        // The forbidden user is excluded from the batch without aborting the rest (D24's
        // per-user tolerance, preserved from M9 D10).
        verify(batchDispatcher).submitBatch(AssistantRunKind.WEEKLY_SUMMARY, List.of(runIdB));
    }

    @Test
    void weekly_anUnexpectedExceptionForOneUserStillReachesTheNextUser() {
        when(accounts.usersOptedIntoExecutionSummaries()).thenReturn(List.of(USER_A, USER_B));
        when(assistantRuns.requestSummary(eq(USER_A), eq(SummaryPeriod.WEEK), any()))
                .thenThrow(new RuntimeException("boom"));
        UUID runIdB = UUID.randomUUID();
        when(assistantRuns.requestSummary(eq(USER_B), eq(SummaryPeriod.WEEK), any()))
                .thenReturn(new AssistantRunView(runIdB, "WEEKLY_SUMMARY", "PENDING", "claude-sonnet-5",
                        null, null, null, null, null, Instant.now(), List.of(), null, null, null));

        scheduler(available).weekly();

        verify(batchDispatcher).submitBatch(AssistantRunKind.WEEKLY_SUMMARY, List.of(runIdB));
    }

    @Test
    void monthly_resolvesFromYesterdayUsingTheGivenClock() {
        when(accounts.usersOptedIntoExecutionSummaries()).thenReturn(List.of(USER_A));
        UUID runId = UUID.randomUUID();
        when(assistantRuns.requestSummary(eq(USER_A), eq(SummaryPeriod.MONTH),
                eq(CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(1))))
                .thenReturn(new AssistantRunView(runId, "MONTHLY_SUMMARY", "PENDING", "claude-sonnet-5",
                        null, null, null, null, null, Instant.now(), List.of(), null, null, null));

        scheduler(available).monthly();

        verify(batchDispatcher).submitBatch(AssistantRunKind.MONTHLY_SUMMARY, List.of(runId));
    }

    @Test
    void weekly_submitsOneBatchForAllQueuedUsersInASingleCall() {
        when(accounts.usersOptedIntoExecutionSummaries()).thenReturn(List.of(USER_A, USER_B));
        UUID runIdA = UUID.randomUUID();
        UUID runIdB = UUID.randomUUID();
        when(assistantRuns.requestSummary(eq(USER_A), eq(SummaryPeriod.WEEK), any()))
                .thenReturn(new AssistantRunView(runIdA, "WEEKLY_SUMMARY", "PENDING", "claude-sonnet-5",
                        null, null, null, null, null, Instant.now(), List.of(), null, null, null));
        when(assistantRuns.requestSummary(eq(USER_B), eq(SummaryPeriod.WEEK), any()))
                .thenReturn(new AssistantRunView(runIdB, "WEEKLY_SUMMARY", "PENDING", "claude-sonnet-5",
                        null, null, null, null, null, Instant.now(), List.of(), null, null, null));

        scheduler(available).weekly();

        // Exactly one submitBatch call per firing (D20), not one per user.
        verify(batchDispatcher, org.mockito.Mockito.times(1))
                .submitBatch(AssistantRunKind.WEEKLY_SUMMARY, List.of(runIdA, runIdB));
    }
}
