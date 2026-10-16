package com.kairon.assistant.app;

import java.time.Duration;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.identity.api.UserAccountApi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D19 extraction out of {@code AssistantRunService} — these five gates and
 * {@code resolveModel} are now shared by both that service and
 * {@link McpAssistantService}, so they get their own direct test file rather
 * than only the indirect exercise {@code AssistantRunServiceTest}'s own
 * (real-instance) tests already give them.
 */
@ExtendWith(MockitoExtension.class)
class AssistantAvailabilityTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000a1"));

    @Mock
    UserAccountApi accounts;

    private static AssistantProperties properties(boolean enabled, String apiKey) {
        return new AssistantProperties(enabled, apiKey, "claude-sonnet-5", Duration.ofSeconds(30), 500_000, null,
                null, null, null, null);
    }

    private static AssistantPreferencesView prefsWith(boolean todo, boolean summaries, boolean reflection,
            boolean generation, boolean editing, String modelOverride) {
        return new AssistantPreferencesView(todo, summaries, reflection, generation, editing, modelOverride,
                "balanced");
    }

    @Test
    void requireTodoSuggestionsAvailable_whenInstanceDisabled_throwsForbiddenAndNeverFetchesPreferences() {
        AssistantAvailability availability = new AssistantAvailability(properties(false, "key"), accounts);

        assertThatThrownBy(() -> availability.requireTodoSuggestionsAvailable(USER))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(accounts, never()).assistantPreferences(USER);
    }

    @Test
    void requireTodoSuggestionsAvailable_whenNoApiKey_throwsForbidden() {
        AssistantAvailability availability = new AssistantAvailability(properties(true, "  "), accounts);

        assertThatThrownBy(() -> availability.requireTodoSuggestionsAvailable(USER))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void requireTodoSuggestionsAvailable_whenNotOptedIn_throwsForbidden() {
        when(accounts.assistantPreferences(USER))
                .thenReturn(prefsWith(false, true, true, true, true, null));
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThatThrownBy(() -> availability.requireTodoSuggestionsAvailable(USER))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void requireTodoSuggestionsAvailable_whenOptedIn_returnsThePreferences() {
        AssistantPreferencesView prefs = prefsWith(true, false, false, false, false, null);
        when(accounts.assistantPreferences(USER)).thenReturn(prefs);
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThat(availability.requireTodoSuggestionsAvailable(USER)).isSameAs(prefs);
    }

    @Test
    void requireProjectGenerationAvailable_whenNotOptedIn_throwsForbidden() {
        when(accounts.assistantPreferences(USER))
                .thenReturn(prefsWith(true, true, true, false, true, null));
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThatThrownBy(() -> availability.requireProjectGenerationAvailable(USER))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void requireProjectEditingAvailable_whenNotOptedIn_throwsForbidden() {
        when(accounts.assistantPreferences(USER))
                .thenReturn(prefsWith(true, true, true, true, false, null));
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThatThrownBy(() -> availability.requireProjectEditingAvailable(USER))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void requireExecutionSummariesAvailable_whenNotOptedIn_throwsForbidden() {
        when(accounts.assistantPreferences(USER))
                .thenReturn(prefsWith(true, false, true, true, true, null));
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThatThrownBy(() -> availability.requireExecutionSummariesAvailable(USER))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void requireJournalReflectionAvailable_whenNotOptedIn_throwsForbidden() {
        when(accounts.assistantPreferences(USER))
                .thenReturn(prefsWith(true, true, false, true, true, null));
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThatThrownBy(() -> availability.requireJournalReflectionAvailable(USER))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
    }

    @Test
    void resolveModel_withAKnownOverride_returnsIt() {
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThat(availability.resolveModel(prefsWith(true, true, true, true, true, "claude-opus-5")))
                .isEqualTo("claude-opus-5");
    }

    @Test
    void resolveModel_withAnUnknownOverride_fallsBackToTheInstanceDefault() {
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThat(availability.resolveModel(prefsWith(true, true, true, true, true, "not-a-real-model")))
                .isEqualTo("claude-sonnet-5");
    }

    @Test
    void resolveModel_withNoOverride_returnsTheInstanceDefault() {
        AssistantAvailability availability = new AssistantAvailability(properties(true, "key"), accounts);

        assertThat(availability.resolveModel(prefsWith(true, true, true, true, true, null)))
                .isEqualTo("claude-sonnet-5");
    }
}
