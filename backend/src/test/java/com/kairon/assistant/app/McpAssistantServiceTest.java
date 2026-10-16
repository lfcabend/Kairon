package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.llm.ProjectEditPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.llm.TaskOperationPayload;
import com.kairon.assistant.repo.AssistantRunRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.identity.api.AssistantPreferencesView;
import com.kairon.journal.api.JournalApi;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectView;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the MCP entry point's own orchestration (docs/milestones/
 * M10.5-mcp-server.md D20) — {@link AssistantAvailability}/
 * {@link AssistantSuggestionWriter} are mocked here (already covered by
 * {@link AssistantRunServiceTest}'s own exercise of the real
 * implementations) so this file isolates what's genuinely new: the per-run
 * {@code markMcp()}/{@code source=MCP} tagging, the zero-token
 * {@code succeed(0, 0)} write-back (D5 — no budget check exists on this
 * path at all), the re-run 404/422 preconditions (D14), and the
 * server-rendered stats table (D13).
 */
@ExtendWith(MockitoExtension.class)
class McpAssistantServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f2"));
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Mock
    AssistantRunRepository runs;

    @Mock
    AssistantAvailability availability;

    @Mock
    AssistantSuggestionWriter writer;

    @Mock
    ProjectsApi projectsApi;

    @Mock
    JournalApi journal;

    @Mock
    TodoSuggestionContextBuilder todoContextBuilder;

    @Mock
    ProjectPlanContextBuilder projectPlanContextBuilder;

    @Mock
    ProjectEditContextBuilder projectEditContextBuilder;

    @Mock
    SummaryContextBuilder summaryContextBuilder;

    @Mock
    JournalReflectionContextBuilder journalReflectionContextBuilder;

    @Mock
    ObjectMapper objectMapper;

    McpAssistantService service;

    @BeforeEach
    void setUp() {
        service = new McpAssistantService(runs, availability, writer, projectsApi, journal, todoContextBuilder,
                projectPlanContextBuilder, projectEditContextBuilder, summaryContextBuilder,
                journalReflectionContextBuilder, objectMapper);
    }

    private static AssistantPreferencesView prefs(String tone) {
        return new AssistantPreferencesView(true, true, true, true, true, null, tone);
    }

    // --- todo suggestions ------------------------------------------------------

    @Test
    void readTodoSuggestionContext_whenNotOptedIn_throwsForbiddenAndNeverBuildsContext() {
        when(availability.requireTodoSuggestionsAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled todo suggestions in Settings."));

        assertThatThrownBy(() -> service.readTodoSuggestionContext(USER, DAY, Horizon.DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(todoContextBuilder, never()).build(any(), any(), any());
    }

    @Test
    void readTodoSuggestionContext_onSuccess_returnsTheRenderedPromptPair() {
        when(todoContextBuilder.build(USER, DAY, Horizon.WEEK))
                .thenReturn(new TodoSuggestionContextBuilder.Context("sys prompt", "user content", Map.of()));

        RenderedContext context = service.readTodoSuggestionContext(USER, DAY, Horizon.WEEK);

        assertThat(context.systemPrompt()).isEqualTo("sys prompt");
        assertThat(context.userContent()).isEqualTo("user content");
    }

    @Test
    void submitTodoSuggestions_checksAvailabilityBeforeAnythingElse() {
        when(availability.requireTodoSuggestionsAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled todo suggestions in Settings."));

        assertThatThrownBy(() -> service.submitTodoSuggestions(USER, DAY, Horizon.DAY, null, List.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(runs, never()).save(any());
    }

    @Test
    void submitTodoSuggestions_onSuccess_persistsAnMcpTaggedSucceededRunWithNoTokenSpend() {
        when(todoContextBuilder.build(USER, DAY, Horizon.DAY))
                .thenReturn(new TodoSuggestionContextBuilder.Context("sys", "user", Map.of()));
        SuggestedTaskPayload payload = new SuggestedTaskPayload("Order hardware", null, "rationale", DAY, 20, null);
        AssistantSuggestedTask task = AssistantSuggestedTask.propose(UUID.randomUUID(), USER.value(),
                "Order hardware", null, "rationale", DAY, 20, null, 0);
        when(writer.saveSuggestions(any(), eq(List.of(payload)), eq(DAY), eq(DAY))).thenReturn(List.of(task));

        AssistantRunView view = service.submitTodoSuggestions(USER, DAY, Horizon.DAY, "claude-opus-5",
                List.of(payload));

        assertThat(view.status()).isEqualTo("SUCCEEDED");
        assertThat(view.kind()).isEqualTo("TODO_SUGGESTION");
        assertThat(view.source()).isEqualTo("MCP");
        assertThat(view.model()).isEqualTo("claude-opus-5");
        assertThat(view.inputTokens()).isEqualTo(0);
        assertThat(view.outputTokens()).isEqualTo(0);

        ArgumentCaptor<com.kairon.assistant.domain.AssistantRun> saved =
                ArgumentCaptor.forClass(com.kairon.assistant.domain.AssistantRun.class);
        verify(runs, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getSource().name()).isEqualTo("MCP");
        // D5 — no per-user monthly token budget check exists on this path at all.
        verify(runs, never()).sumTokensSince(any(), any());
    }

    @Test
    void submitTodoSuggestions_withNoModel_defaultsToMcp() {
        when(todoContextBuilder.build(USER, DAY, Horizon.DAY))
                .thenReturn(new TodoSuggestionContextBuilder.Context("sys", "user", Map.of()));
        when(writer.saveSuggestions(any(), any(), any(), any())).thenReturn(List.of());

        AssistantRunView view = service.submitTodoSuggestions(USER, DAY, Horizon.DAY, null, List.of());

        assertThat(view.model()).isEqualTo("mcp");
    }

    // --- project plan ------------------------------------------------------------

    @Test
    void readProjectPlanContext_whenNotOptedIn_throwsForbiddenAndNeverBuildsContext() {
        when(availability.requireProjectGenerationAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled project generation in Settings."));

        assertThatThrownBy(() -> service.readProjectPlanContext(USER, "Kitchen remodel", DAY, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(projectPlanContextBuilder, never()).build(any(), any(), any(), any());
    }

    @Test
    void readProjectPlanContext_onSuccess_returnsTheRenderedPromptPair() {
        when(projectPlanContextBuilder.build(eq(USER), eq("Kitchen remodel"), eq(DAY), any()))
                .thenReturn(new ProjectPlanContextBuilder.Context("sys prompt", "user content", Map.of(), List.of()));

        RenderedContext context = service.readProjectPlanContext(USER, "Kitchen remodel", DAY, null);

        assertThat(context.systemPrompt()).isEqualTo("sys prompt");
        assertThat(context.userContent()).isEqualTo("user content");
    }

    @Test
    void submitProjectPlan_whenNotOptedIn_throwsForbiddenAndSavesNothing() {
        when(availability.requireProjectGenerationAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled project generation in Settings."));
        ProjectPlanPayload payload = new ProjectPlanPayload("Kitchen remodel", "desc", "M", null, List.of(),
                List.of());

        assertThatThrownBy(() -> service.submitProjectPlan(USER, "Kitchen remodel", DAY, null, null, payload))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(runs, never()).save(any());
        verify(writer, never()).saveSuggestedProject(any(), any(), any(), any(), any());
    }

    @Test
    void submitProjectPlan_onSuccess_persistsAnMcpTaggedProposedPlan() {
        when(projectPlanContextBuilder.build(eq(USER), eq("Kitchen remodel"), eq(DAY), any()))
                .thenReturn(new ProjectPlanContextBuilder.Context("sys", "user", Map.of(), List.of()));
        ProjectPlanPayload payload = new ProjectPlanPayload("Kitchen remodel", "desc", "M", null, List.of(),
                List.of());
        AssistantSuggestedProject saved = AssistantSuggestedProject.propose(UUID.randomUUID(), USER.value(),
                Map.of());
        when(writer.saveSuggestedProject(any(), eq(payload), eq(DAY), any(), any())).thenReturn(saved);
        PersistedProjectPlan persisted = new PersistedProjectPlan("Kitchen remodel", "desc", "M", DAY, null, null,
                null, List.of(), List.of());
        when(objectMapper.convertValue(any(), eq(PersistedProjectPlan.class))).thenReturn(persisted);

        AssistantRunView view = service.submitProjectPlan(USER, "Kitchen remodel", DAY, null, "claude-sonnet-5",
                payload);

        assertThat(view.kind()).isEqualTo("PROJECT_GENERATION");
        assertThat(view.source()).isEqualTo("MCP");
        assertThat(view.suggestedProject()).isNotNull();
        assertThat(view.suggestedProject().status()).isEqualTo("PROPOSED");
    }

    // --- project edit --------------------------------------------------------------

    @Test
    void readProjectEditContext_whenNotOptedIn_throwsForbiddenAndNeverChecksTheProject() {
        when(availability.requireProjectEditingAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled project editing in Settings."));

        assertThatThrownBy(() -> service.readProjectEditContext(USER, UUID.randomUUID(), "add a task"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(projectEditContextBuilder, never()).build(any(), any(), any());
    }

    @Test
    void readProjectEditContext_onSuccess_returnsTheRenderedPromptPair() {
        UUID projectId = UUID.randomUUID();
        when(projectEditContextBuilder.build(eq(USER), eq(projectId), eq("add a task")))
                .thenReturn(new ProjectEditContextBuilder.Context("sys prompt", "user content", Map.of(), List.of()));

        RenderedContext context = service.readProjectEditContext(USER, projectId, "add a task");

        assertThat(context.systemPrompt()).isEqualTo("sys prompt");
        assertThat(context.userContent()).isEqualTo("user content");
    }

    @Test
    void submitProjectEdit_whenNotOptedIn_throwsForbiddenAndNeverChecksTheProject() {
        when(availability.requireProjectEditingAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled project editing in Settings."));
        ProjectEditPayload diff = new ProjectEditPayload(null, List.of(), List.of(), List.of());

        assertThatThrownBy(
                () -> service.submitProjectEdit(USER, UUID.randomUUID(), "add a task", null, diff))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(projectsApi, never()).requireProject(any(), any());
        verify(runs, never()).save(any());
    }

    @Test
    void submitProjectEdit_whenProjectNotOwned_throws404BeforeSavingAnything() {
        UUID projectId = UUID.randomUUID();
        when(projectsApi.requireProject(USER, projectId)).thenThrow(ApiException.notFound("Project not found."));
        TaskOperationPayload op = new TaskOperationPayload("ADD", null, "n1", null, "Inspection", null, false, DAY,
                DAY, null);
        ProjectEditPayload diff = new ProjectEditPayload(null, List.of(op), List.of(), List.of());

        assertThatThrownBy(
                () -> service.submitProjectEdit(USER, projectId, "add a task", null, diff))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
        verify(runs, never()).save(any());
        verify(writer, never()).saveSuggestedEdit(any(), any(), any(), any());
    }

    @Test
    void submitProjectEdit_onSuccess_persistsAnMcpTaggedProposedDiff() {
        UUID projectId = UUID.randomUUID();
        when(projectsApi.requireProject(USER, projectId)).thenReturn(project(projectId));
        when(projectEditContextBuilder.build(eq(USER), eq(projectId), eq("add a task")))
                .thenReturn(new ProjectEditContextBuilder.Context("sys", "user", Map.of(), List.of()));
        TaskOperationPayload op = new TaskOperationPayload("ADD", null, "n1", null, "Inspection", null, false, DAY,
                DAY, null);
        ProjectEditPayload diff = new ProjectEditPayload(null, List.of(op), List.of(), List.of());
        AssistantSuggestedProjectEdit saved = AssistantSuggestedProjectEdit.propose(UUID.randomUUID(), USER.value(),
                projectId, Map.of());
        when(writer.saveSuggestedEdit(any(), eq(projectId), eq(diff), any())).thenReturn(saved);
        PersistedProjectEdit persisted = new PersistedProjectEdit(null, List.of(op), List.of(), List.of());
        when(objectMapper.convertValue(any(), eq(PersistedProjectEdit.class))).thenReturn(persisted);

        AssistantRunView view = service.submitProjectEdit(USER, projectId, "add a task", null, diff);

        assertThat(view.kind()).isEqualTo("PROJECT_EDIT");
        assertThat(view.source()).isEqualTo("MCP");
        assertThat(view.suggestedProjectEdit()).isNotNull();
        assertThat(view.suggestedProjectEdit().taskOperations()).hasSize(1);
    }

    private static ProjectView project(UUID id) {
        return new ProjectView(id, null, "Kitchen remodel", null, "ACTIVE", null, 100, 100, "#6366f1", DAY, DAY,
                null, null, java.time.Instant.now(), java.time.Instant.now(), 0);
    }

    // --- summary --------------------------------------------------------------------

    @Test
    void readSummaryContext_whenNotOptedIn_throwsForbiddenAndNeverBuildsContext() {
        when(availability.requireExecutionSummariesAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled execution summaries in Settings."));

        assertThatThrownBy(() -> service.readSummaryContext(USER, SummaryPeriod.WEEK, DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(summaryContextBuilder, never()).build(any(), any(), any(), any());
    }

    @Test
    void readSummaryContext_onSuccess_returnsTheRenderedPromptPairWithoutTheStatsTable() {
        SummaryContextBuilder.Context context = new SummaryContextBuilder.Context("sys prompt", "user content",
                Map.of(), new com.kairon.todo.api.TodoApi.PeriodStats(3, 2, 0), List.of(), List.of(), List.of());
        when(summaryContextBuilder.build(eq(USER), eq(com.kairon.assistant.domain.AssistantRunKind.WEEKLY_SUMMARY),
                any(), any())).thenReturn(context);

        RenderedContext rendered = service.readSummaryContext(USER, SummaryPeriod.WEEK, DAY);

        assertThat(rendered.systemPrompt()).isEqualTo("sys prompt");
        assertThat(rendered.userContent()).isEqualTo("user content");
    }

    @Test
    void submitSummary_whenNotOptedIn_throwsForbiddenAndCreatesNoRun() {
        when(availability.requireExecutionSummariesAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled execution summaries in Settings."));

        assertThatThrownBy(() -> service.submitSummary(USER, SummaryPeriod.WEEK, DAY, null, "The week went well."))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(runs, never()).save(any());
    }

    @Test
    void submitSummary_appendsTheServerRenderedStatsTableAfterTheSubmittedNarrative() {
        SummaryContextBuilder.Context context = new SummaryContextBuilder.Context("sys", "user", Map.of(),
                new com.kairon.todo.api.TodoApi.PeriodStats(3, 2, 0), List.of(), List.of(), List.of());
        when(summaryContextBuilder.build(eq(USER), eq(com.kairon.assistant.domain.AssistantRunKind.WEEKLY_SUMMARY),
                any(), any())).thenReturn(context);

        AssistantRunView view = service.submitSummary(USER, SummaryPeriod.WEEK, DAY, null, "The week went well.");

        assertThat(view.kind()).isEqualTo("WEEKLY_SUMMARY");
        assertThat(view.source()).isEqualTo("MCP");
        assertThat(view.outputMarkdown()).startsWith("The week went well.");
        assertThat(view.outputMarkdown()).contains("## Stats");
    }

    // --- journal reflection -----------------------------------------------------------

    @Test
    void readJournalReflectionContext_whenNotOptedIn_throwsForbiddenAndNeverChecksTheWeek() {
        when(availability.requireJournalReflectionAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled journal reflection in Settings."));

        assertThatThrownBy(() -> service.readJournalReflectionContext(USER, DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(journal, never()).range(any(), any(), any());
    }

    @Test
    void readJournalReflectionContext_onSuccess_returnsTheRenderedPromptPair() {
        when(availability.requireJournalReflectionAvailable(USER)).thenReturn(prefs("encouraging"));
        JournalEntryView entry = new JournalEntryView(UUID.randomUUID(), DAY, 0, null, "Content", 3,
                java.time.Instant.now(), java.time.Instant.now(), 0);
        when(journal.range(eq(USER), any(), any())).thenReturn(List.of(entry));
        when(journalReflectionContextBuilder.build(eq(USER), any(), any(), eq("encouraging")))
                .thenReturn(new JournalReflectionContextBuilder.Context("sys prompt", "user content", Map.of()));

        RenderedContext context = service.readJournalReflectionContext(USER, DAY);

        assertThat(context.systemPrompt()).isEqualTo("sys prompt");
        assertThat(context.userContent()).isEqualTo("user content");
    }

    @Test
    void submitJournalReflection_whenNotOptedIn_throwsForbiddenAndCreatesNoRun() {
        when(availability.requireJournalReflectionAvailable(USER))
                .thenThrow(ApiException.forbidden("You haven't enabled journal reflection in Settings."));

        assertThatThrownBy(() -> service.submitJournalReflection(USER, DAY, null, "A quiet week."))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(runs, never()).save(any());
        verify(journal, never()).range(any(), any(), any());
    }

    @Test
    void readJournalReflectionContext_whenWeekHasNoEntries_throwsUnprocessable() {
        when(availability.requireJournalReflectionAvailable(USER)).thenReturn(prefs("balanced"));
        when(journal.range(eq(USER), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.readJournalReflectionContext(USER, DAY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(422));
    }

    @Test
    void submitJournalReflection_whenWeekHasNoEntries_throwsUnprocessableAndCreatesNoRun() {
        when(availability.requireJournalReflectionAvailable(USER)).thenReturn(prefs("balanced"));
        when(journal.range(eq(USER), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitJournalReflection(USER, DAY, null, "A quiet week."))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(422));
        verify(runs, never()).save(any());
    }

    @Test
    void submitJournalReflection_onSuccess_persistsAnMcpTaggedRunWithNoStatsTable() {
        when(availability.requireJournalReflectionAvailable(USER)).thenReturn(prefs("direct"));
        JournalEntryView entry = new JournalEntryView(UUID.randomUUID(), DAY, 0, null, "Content", 3,
                java.time.Instant.now(), java.time.Instant.now(), 0);
        when(journal.range(eq(USER), any(), any())).thenReturn(List.of(entry));
        when(journalReflectionContextBuilder.build(eq(USER), any(), any(), eq("direct")))
                .thenReturn(new JournalReflectionContextBuilder.Context("sys", "user", Map.of()));

        AssistantRunView view = service.submitJournalReflection(USER, DAY, null, "A thoughtful week.");

        assertThat(view.kind()).isEqualTo("JOURNAL_REFLECTION");
        assertThat(view.source()).isEqualTo("MCP");
        assertThat(view.outputMarkdown()).isEqualTo("A thoughtful week.");
    }
}
