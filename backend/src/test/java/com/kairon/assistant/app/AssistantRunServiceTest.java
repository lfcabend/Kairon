package com.kairon.assistant.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.ProjectPlanRequest;
import com.kairon.assistant.llm.AnthropicClient.ProjectPlanResult;
import com.kairon.assistant.llm.AnthropicClient.TodoSuggestionRequest;
import com.kairon.assistant.llm.AnthropicClient.TodoSuggestionsResult;
import com.kairon.assistant.llm.PlannedTaskPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.assistant.repo.AssistantSuggestedProjectRepository;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.identity.api.UserAccountApi;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantRunServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T08:00:00Z"), ZoneOffset.UTC);

    @Mock
    AssistantRunRepository runs;

    @Mock
    AssistantSuggestedTaskRepository suggestedTasks;

    @Mock
    AssistantSuggestedProjectRepository suggestedProjects;

    @Mock
    UserAccountApi accounts;

    @Mock
    TodoSuggestionContextBuilder contextBuilder;

    @Mock
    ProjectPlanContextBuilder projectPlanContextBuilder;

    @Mock
    AnthropicClient anthropicClient;

    @Mock
    ObjectMapper objectMapper;

    private AssistantProperties properties;
    private AssistantRunService service;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties(true, "sk-test-key", "claude-sonnet-5", Duration.ofSeconds(30),
                500_000, new AssistantProperties.TodoSuggestions(7, 21, 5, 5, "MEDIUM"),
                new AssistantProperties.ProjectPlan(40));
        service = newService();
        stubPlanRoundTrip();
    }

    private AssistantRunService newService() {
        return new AssistantRunService(runs, suggestedTasks, suggestedProjects, properties, accounts, contextBuilder,
                projectPlanContextBuilder, anthropicClient, objectMapper, CLOCK);
    }

    @SuppressWarnings("unchecked")
    private void stubPlanRoundTrip() {
        // The service round-trips a plan through the ObjectMapper (typed -> Map for
        // storage, Map -> typed for the response) — stub both directions to return
        // whatever was passed straight through, matching real Jackson behavior for
        // this test's purposes. lenient(): most tests never touch the project-plan
        // path at all, so these stubs would otherwise fail Mockito's strict-stubbing
        // "unused stub" check on every one of them.
        org.mockito.Mockito.lenient().when(objectMapper.convertValue(any(), eq(Map.class)))
                .thenAnswer(inv -> Map.of());
        org.mockito.Mockito.lenient().when(objectMapper.convertValue(any(), eq(PersistedProjectPlan.class)))
                .thenAnswer(inv -> lastPersistedPlan);
    }

    // Set by tests that need the "stored -> re-read" round trip to reflect a specific plan.
    private PersistedProjectPlan lastPersistedPlan;

    private static AssistantPreferencesView optedIn() {
        return new AssistantPreferencesView(true, false, false, true, null, "balanced");
    }

    private static AssistantPreferencesView optedInToProjectGeneration() {
        return new AssistantPreferencesView(false, false, false, true, null, "balanced");
    }

    private static TodoSuggestionContextBuilder.Context context() {
        return new TodoSuggestionContextBuilder.Context("system", "user content", Map.of("systemPrompt", "system"));
    }

    private static ProjectPlanContextBuilder.Context planContext() {
        return new ProjectPlanContextBuilder.Context("system", "user content", Map.of("systemPrompt", "system"));
    }

    @Test
    void requestTodoSuggestions_whenInstanceDisabled_throwsForbiddenAndNeverBuildsContext() {
        properties = new AssistantProperties(false, "sk-test-key", null, null, 0, null, null);
        service = newService();

        assertThatThrownBy(() -> service.requestTodoSuggestions(USER, DAY, Horizon.DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(contextBuilder, never()).build(any(), any(), any());
        verify(accounts, never()).assistantPreferences(any());
    }

    @Test
    void requestTodoSuggestions_whenNoApiKey_throwsForbidden() {
        properties = new AssistantProperties(true, "  ", null, null, 0, null, null);
        service = newService();

        assertThatThrownBy(() -> service.requestTodoSuggestions(USER, DAY, Horizon.DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void requestTodoSuggestions_whenNotOptedIn_throwsForbiddenAndNeverBuildsContext() {
        when(accounts.assistantPreferences(USER))
                .thenReturn(new AssistantPreferencesView(false, false, false, false, null, "balanced"));

        assertThatThrownBy(() -> service.requestTodoSuggestions(USER, DAY, Horizon.DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(contextBuilder, never()).build(any(), any(), any());
    }

    @Test
    void requestTodoSuggestions_whenBudgetAlreadyAtOrOverLimit_throwsForbiddenAndNeverCallsTheClient() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedIn());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(500_000L);

        assertThatThrownBy(() -> service.requestTodoSuggestions(USER, DAY, Horizon.DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(anthropicClient, never()).suggestTodos(any());
    }

    @Test
    void requestTodoSuggestions_onSuccess_savesTheRunAndSuggestionsAndReturnsAView() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedIn());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(contextBuilder.build(USER, DAY, Horizon.DAY)).thenReturn(context());
        SuggestedTaskPayload payload = new SuggestedTaskPayload("Order cabinet hardware", null,
                "Overdue kitchen-remodel task", DAY, 20, null);
        when(anthropicClient.suggestTodos(any(TodoSuggestionRequest.class)))
                .thenReturn(new TodoSuggestionsResult(List.of(payload), "claude-sonnet-5", 1840, 310));

        AssistantRunView view = service.requestTodoSuggestions(USER, DAY, Horizon.DAY);

        assertThat(view.status()).isEqualTo("SUCCEEDED");
        assertThat(view.kind()).isEqualTo("TODO_SUGGESTION");
        assertThat(view.inputTokens()).isEqualTo(1840);
        assertThat(view.outputTokens()).isEqualTo(310);
        assertThat(view.suggestions()).hasSize(1);
        assertThat(view.suggestions().get(0).title()).isEqualTo("Order cabinet hardware");
        assertThat(view.suggestedProject()).isNull();
        verify(suggestedTasks, times(1)).save(any());
    }

    @Test
    void requestTodoSuggestions_capsSuggestionsAtTheConfiguredMax() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedIn());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(contextBuilder.build(USER, DAY, Horizon.DAY)).thenReturn(context());
        List<SuggestedTaskPayload> sevenSuggestions = java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> new SuggestedTaskPayload("Task " + i, null, "rationale", DAY, null, null))
                .toList();
        when(anthropicClient.suggestTodos(any(TodoSuggestionRequest.class)))
                .thenReturn(new TodoSuggestionsResult(sevenSuggestions, "claude-sonnet-5", 100, 100));

        AssistantRunView view = service.requestTodoSuggestions(USER, DAY, Horizon.DAY);

        assertThat(view.suggestions()).hasSize(5);
        verify(suggestedTasks, times(5)).save(any());
    }

    @Test
    void requestTodoSuggestions_clampsAnOutOfRangeSuggestedForDayIntoTheRequestedWindow() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedIn());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(contextBuilder.build(USER, DAY, Horizon.WEEK)).thenReturn(context());
        SuggestedTaskPayload outOfRange = new SuggestedTaskPayload("Task", null, "rationale",
                DAY.plusDays(30), null, null);
        when(anthropicClient.suggestTodos(any(TodoSuggestionRequest.class)))
                .thenReturn(new TodoSuggestionsResult(List.of(outOfRange), "claude-sonnet-5", 100, 100));

        AssistantRunView view = service.requestTodoSuggestions(USER, DAY, Horizon.WEEK);

        assertThat(view.suggestions().get(0).suggestedForDay()).isEqualTo(DAY.plusDays(6));
    }

    @Test
    void requestTodoSuggestions_onUpstreamFailure_marksTheRunFailedAndRethrowsAsAProblem() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedIn());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(contextBuilder.build(USER, DAY, Horizon.DAY)).thenReturn(context());
        when(anthropicClient.suggestTodos(any(TodoSuggestionRequest.class)))
                .thenThrow(new AssistantUpstreamException(true, "The assistant is temporarily unavailable.", null));

        assertThatThrownBy(() -> service.requestTodoSuggestions(USER, DAY, Horizon.DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(503));
        verify(suggestedTasks, never()).save(any());

        ArgumentCaptor<com.kairon.assistant.domain.AssistantRun> saved =
                ArgumentCaptor.forClass(com.kairon.assistant.domain.AssistantRun.class);
        verify(runs, times(3)).save(saved.capture());
        assertThat(saved.getValue().getStatus().name()).isEqualTo("FAILED");
    }

    @Test
    void requestTodoSuggestions_withAnUnknownModelOverride_fallsBackToTheInstanceDefault() {
        when(accounts.assistantPreferences(USER)).thenReturn(
                new AssistantPreferencesView(true, false, false, false, "not-a-real-model", "balanced"));
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(contextBuilder.build(USER, DAY, Horizon.DAY)).thenReturn(context());
        when(anthropicClient.suggestTodos(any(TodoSuggestionRequest.class)))
                .thenReturn(new TodoSuggestionsResult(List.of(), "claude-sonnet-5", 10, 10));

        AssistantRunView view = service.requestTodoSuggestions(USER, DAY, Horizon.DAY);

        assertThat(view.model()).isEqualTo("claude-sonnet-5");
    }

    // --- requestProjectPlan (M8.5) --------------------------------------------

    @Test
    void requestProjectPlan_whenInstanceDisabled_throwsForbiddenAndNeverBuildsContext() {
        properties = new AssistantProperties(false, "sk-test-key", null, null, 0, null, null);
        service = newService();

        assertThatThrownBy(() -> service.requestProjectPlan(USER, "Kitchen remodel", DAY, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(projectPlanContextBuilder, never()).build(any(), any(), any());
        verify(accounts, never()).assistantPreferences(any());
    }

    @Test
    void requestProjectPlan_whenNotOptedIn_throwsForbidden() {
        when(accounts.assistantPreferences(USER))
                .thenReturn(new AssistantPreferencesView(true, false, false, false, null, "balanced"));

        assertThatThrownBy(() -> service.requestProjectPlan(USER, "Kitchen remodel", DAY, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(projectPlanContextBuilder, never()).build(any(), any(), any());
    }

    @Test
    void requestProjectPlan_whenBudgetAlreadyAtOrOverLimit_throwsForbiddenAndNeverCallsTheClient() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedInToProjectGeneration());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(500_000L);

        assertThatThrownBy(() -> service.requestProjectPlan(USER, "Kitchen remodel", DAY, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(anthropicClient, never()).generateProjectPlan(any());
    }

    @Test
    void requestProjectPlan_onSuccess_persistsSucceededRunAndAProposedPlan() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedInToProjectGeneration());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(projectPlanContextBuilder.build(eq("Kitchen remodel"), eq(DAY), any())).thenReturn(planContext());
        PlannedTaskPayload task = new PlannedTaskPayload("t1", null, "Design", null, false, DAY, DAY.plusDays(6),
                null);
        ProjectPlanPayload payload = new ProjectPlanPayload("Kitchen remodel", "desc", "M", List.of(task),
                List.of());
        when(anthropicClient.generateProjectPlan(any(ProjectPlanRequest.class)))
                .thenReturn(new ProjectPlanResult(payload, "claude-sonnet-5", 640, 890));
        lastPersistedPlan = new PersistedProjectPlan("Kitchen remodel", "desc", "M", DAY, null, List.of(task),
                List.of());

        AssistantRunView view = service.requestProjectPlan(USER, "Kitchen remodel", DAY, null);

        assertThat(view.status()).isEqualTo("SUCCEEDED");
        assertThat(view.kind()).isEqualTo("PROJECT_GENERATION");
        assertThat(view.inputTokens()).isEqualTo(640);
        assertThat(view.outputTokens()).isEqualTo(890);
        assertThat(view.suggestions()).isEmpty();
        assertThat(view.suggestedProject()).isNotNull();
        assertThat(view.suggestedProject().status()).isEqualTo("PROPOSED");
        assertThat(view.suggestedProject().tasks()).hasSize(1);
        verify(suggestedProjects, times(1)).save(any(AssistantSuggestedProject.class));
    }

    @Test
    void requestProjectPlan_normalizesABlankParentKeyOnATopLevelTaskToNull() {
        // Observed against the real API: the model sends "" rather than omitting
        // parentKey (or sending JSON null) for a genuinely top-level task. Left
        // un-normalized, every downstream root/child check treats parentKey == null
        // as "top-level" and would flatten the whole tree (see AssistantRunService).
        when(accounts.assistantPreferences(USER)).thenReturn(optedInToProjectGeneration());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(projectPlanContextBuilder.build(any(), any(), any())).thenReturn(planContext());
        PlannedTaskPayload blankParent = new PlannedTaskPayload("t1", "", "Design", null, false, DAY, DAY, null);
        PlannedTaskPayload realChild = new PlannedTaskPayload("t2", "t1", "Pick materials", null, false, DAY, DAY,
                null);
        ProjectPlanPayload payload = new ProjectPlanPayload("Kitchen remodel", "desc", "M",
                List.of(blankParent, realChild), List.of());
        when(anthropicClient.generateProjectPlan(any(ProjectPlanRequest.class)))
                .thenReturn(new ProjectPlanResult(payload, "claude-sonnet-5", 100, 100));
        lastPersistedPlan = new PersistedProjectPlan("Kitchen remodel", "desc", "M", DAY, null,
                List.of(blankParent, realChild), List.of());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> persistedCaptor = ArgumentCaptor.forClass(Object.class);

        service.requestProjectPlan(USER, "Kitchen remodel", DAY, null);

        verify(objectMapper).convertValue(persistedCaptor.capture(), eq(Map.class));
        PersistedProjectPlan persisted = (PersistedProjectPlan) persistedCaptor.getValue();
        assertThat(persisted.tasks().get(0).parentKey()).isNull();
        assertThat(persisted.tasks().get(1).parentKey()).isEqualTo("t1");
    }

    @Test
    void requestProjectPlan_capsTasksAtTheConfiguredMax() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedInToProjectGeneration());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(projectPlanContextBuilder.build(any(), any(), any())).thenReturn(planContext());
        properties = new AssistantProperties(true, "sk-test-key", "claude-sonnet-5", Duration.ofSeconds(30),
                500_000, null, new AssistantProperties.ProjectPlan(2));
        service = newService();
        List<PlannedTaskPayload> fiveTasks = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> new PlannedTaskPayload("t" + i, null, "Task " + i, null, false, DAY, DAY, null))
                .toList();
        ProjectPlanPayload payload = new ProjectPlanPayload("Big project", "desc", "L", fiveTasks, List.of());
        when(anthropicClient.generateProjectPlan(any(ProjectPlanRequest.class)))
                .thenReturn(new ProjectPlanResult(payload, "claude-sonnet-5", 100, 100));
        lastPersistedPlan = new PersistedProjectPlan("Big project", "desc", "L", DAY, null,
                fiveTasks.subList(0, 2), List.of());

        AssistantRunView view = service.requestProjectPlan(USER, "Big project", DAY, null);

        assertThat(view.suggestedProject().tasks()).hasSize(2);
    }

    @Test
    void requestProjectPlan_onUpstreamFailure_marksTheRunFailedAndRethrowsAsAProblem() {
        when(accounts.assistantPreferences(USER)).thenReturn(optedInToProjectGeneration());
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(projectPlanContextBuilder.build(any(), any(), any())).thenReturn(planContext());
        when(anthropicClient.generateProjectPlan(any(ProjectPlanRequest.class)))
                .thenThrow(new AssistantUpstreamException(false, "The assistant could not complete this request.",
                        null));

        assertThatThrownBy(() -> service.requestProjectPlan(USER, "Kitchen remodel", DAY, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(502));
        verify(suggestedProjects, never()).save(any());
    }
}
