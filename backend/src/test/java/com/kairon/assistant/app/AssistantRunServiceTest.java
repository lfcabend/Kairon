package com.kairon.assistant.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.TodoSuggestionRequest;
import com.kairon.assistant.llm.AnthropicClient.TodoSuggestionsResult;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.identity.api.UserAccountApi;

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
    UserAccountApi accounts;

    @Mock
    TodoSuggestionContextBuilder contextBuilder;

    @Mock
    AnthropicClient anthropicClient;

    private AssistantProperties properties;
    private AssistantRunService service;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties(true, "sk-test-key", "claude-sonnet-5", Duration.ofSeconds(30),
                500_000, new AssistantProperties.TodoSuggestions(7, 21, 5, 5, "MEDIUM"));
        service = new AssistantRunService(runs, suggestedTasks, properties, accounts, contextBuilder,
                anthropicClient, CLOCK);
    }

    private static AssistantPreferencesView optedIn() {
        return new AssistantPreferencesView(true, false, false, null, "balanced");
    }

    private static TodoSuggestionContextBuilder.Context context() {
        return new TodoSuggestionContextBuilder.Context("system", "user content", Map.of("systemPrompt", "system"));
    }

    @Test
    void requestTodoSuggestions_whenInstanceDisabled_throwsForbiddenAndNeverBuildsContext() {
        properties = new AssistantProperties(false, "sk-test-key", null, null, 0, null);
        service = new AssistantRunService(runs, suggestedTasks, properties, accounts, contextBuilder,
                anthropicClient, CLOCK);

        assertThatThrownBy(() -> service.requestTodoSuggestions(USER, DAY, Horizon.DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(contextBuilder, never()).build(any(), any(), any());
        verify(accounts, never()).assistantPreferences(any());
    }

    @Test
    void requestTodoSuggestions_whenNoApiKey_throwsForbidden() {
        properties = new AssistantProperties(true, "  ", null, null, 0, null);
        service = new AssistantRunService(runs, suggestedTasks, properties, accounts, contextBuilder,
                anthropicClient, CLOCK);

        assertThatThrownBy(() -> service.requestTodoSuggestions(USER, DAY, Horizon.DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void requestTodoSuggestions_whenNotOptedIn_throwsForbiddenAndNeverBuildsContext() {
        when(accounts.assistantPreferences(USER))
                .thenReturn(new AssistantPreferencesView(false, false, false, null, "balanced"));

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
                new AssistantPreferencesView(true, false, false, "not-a-real-model", "balanced"));
        when(runs.sumTokensSince(eq(USER.value()), any())).thenReturn(0L);
        when(contextBuilder.build(USER, DAY, Horizon.DAY)).thenReturn(context());
        when(anthropicClient.suggestTodos(any(TodoSuggestionRequest.class)))
                .thenReturn(new TodoSuggestionsResult(List.of(), "claude-sonnet-5", 10, 10));

        AssistantRunView view = service.requestTodoSuggestions(USER, DAY, Horizon.DAY);

        assertThat(view.model()).isEqualTo("claude-sonnet-5");
    }
}
