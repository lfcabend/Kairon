package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.kairon.assistant.domain.AssistantRun;
import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.domain.AssistantSuggestedTask;
import com.kairon.assistant.llm.PlannedTaskPayload;
import com.kairon.assistant.llm.ProjectEditPayload;
import com.kairon.assistant.llm.ProjectFieldChangesPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.assistant.llm.SuggestedTaskPayload;
import com.kairon.assistant.llm.TaskOperationPayload;
import com.kairon.assistant.repo.AssistantSuggestedProjectEditRepository;
import com.kairon.assistant.repo.AssistantSuggestedProjectRepository;
import com.kairon.assistant.repo.AssistantSuggestedTaskRepository;
import com.kairon.projects.api.ProjectsApi.ProjectCategorySummary;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D19 extraction out of {@code AssistantRunService} — the capping/clamping/
 * category-resolution rules now shared by that service and
 * {@link McpAssistantService}, pinned here directly rather than only via
 * {@code AssistantRunServiceTest}'s indirect (real-instance) exercise.
 */
@ExtendWith(MockitoExtension.class)
class AssistantSuggestionWriterTest {

    private static final UUID USER_ID = UUID.fromString("018f5b3e-0000-7000-8000-0000000000a2");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Mock
    AssistantSuggestedTaskRepository suggestedTasks;

    @Mock
    AssistantSuggestedProjectRepository suggestedProjects;

    @Mock
    AssistantSuggestedProjectEditRepository suggestedProjectEdits;

    @Mock
    ObjectMapper objectMapper;

    private AssistantRun run;

    @BeforeEach
    void setUp() {
        run = AssistantRun.pending(USER_ID, com.kairon.assistant.domain.AssistantRunKind.TODO_SUGGESTION, DAY, DAY,
                "mcp");
    }

    private AssistantSuggestionWriter writer(int maxSuggestions, int maxTasks, int maxOperations) {
        AssistantProperties properties = new AssistantProperties(true, "key", "claude-sonnet-5",
                java.time.Duration.ofSeconds(30), 500_000,
                new AssistantProperties.TodoSuggestions(7, 21, 5, maxSuggestions, "MEDIUM"),
                new AssistantProperties.ProjectPlan(maxTasks), null, null,
                new AssistantProperties.ProjectEdit(maxOperations));
        return new AssistantSuggestionWriter(properties, suggestedTasks, suggestedProjects, suggestedProjectEdits,
                objectMapper);
    }

    // --- saveSuggestions ---------------------------------------------------------

    @Test
    void saveSuggestions_capsAtTheConfiguredMax() {
        AssistantSuggestionWriter writer = writer(2, 40, 60);
        List<SuggestedTaskPayload> five = IntStream.range(0, 5)
                .mapToObj(i -> new SuggestedTaskPayload("Task " + i, null, "rationale", DAY, null, null))
                .toList();

        List<AssistantSuggestedTask> result = writer.saveSuggestions(run, five, DAY, DAY);

        assertThat(result).hasSize(2);
        verify(suggestedTasks, times(2)).save(any());
    }

    @Test
    void saveSuggestions_clampsAnOutOfRangeSuggestedForDayIntoTheRequestedWindow() {
        AssistantSuggestionWriter writer = writer(5, 40, 60);
        SuggestedTaskPayload outOfRange = new SuggestedTaskPayload("Task", null, "rationale", DAY.plusDays(30),
                null, null);

        List<AssistantSuggestedTask> result = writer.saveSuggestions(run, List.of(outOfRange), DAY, DAY.plusDays(6));

        assertThat(result.get(0).getSuggestedForDay()).isEqualTo(DAY.plusDays(6));
    }

    @Test
    void saveSuggestions_withANullSuggestedForDay_defaultsToTheRangeStart() {
        AssistantSuggestionWriter writer = writer(5, 40, 60);
        SuggestedTaskPayload noDay = new SuggestedTaskPayload("Task", null, "rationale", null, null, null);

        List<AssistantSuggestedTask> result = writer.saveSuggestions(run, List.of(noDay), DAY, DAY.plusDays(6));

        assertThat(result.get(0).getSuggestedForDay()).isEqualTo(DAY);
    }

    // --- saveSuggestedProject ------------------------------------------------------

    @Test
    void saveSuggestedProject_capsTasksAtTheConfiguredMax() {
        AssistantSuggestionWriter writer = writer(5, 2, 60);
        List<PlannedTaskPayload> fiveTasks = IntStream.range(0, 5)
                .mapToObj(i -> new PlannedTaskPayload("t" + i, null, "Task " + i, null, false, DAY, DAY, null))
                .toList();
        ProjectPlanPayload payload = new ProjectPlanPayload("Big project", "desc", "L", null, fiveTasks, List.of());

        ArgumentCaptor<Map<String, Object>> planCaptor = captureConvertValue();
        writer.saveSuggestedProject(run, payload, DAY, DAY.plusDays(30), List.of());
        PersistedProjectPlan persisted = reconvertPlan(planCaptor);

        assertThat(persisted.tasks()).hasSize(2);
    }

    @Test
    void saveSuggestedProject_normalizesABlankParentKeyOnATopLevelTaskToNull() {
        AssistantSuggestionWriter writer = writer(5, 40, 60);
        PlannedTaskPayload blankParent = new PlannedTaskPayload("t1", "", "Design", null, false, DAY, DAY, null);
        PlannedTaskPayload realChild = new PlannedTaskPayload("t2", "t1", "Pick materials", null, false, DAY, DAY,
                null);
        ProjectPlanPayload payload = new ProjectPlanPayload("Kitchen remodel", "desc", "M", null,
                List.of(blankParent, realChild), List.of());

        ArgumentCaptor<Map<String, Object>> planCaptor = captureConvertValue();
        writer.saveSuggestedProject(run, payload, DAY, DAY.plusDays(30), List.of());
        PersistedProjectPlan persisted = reconvertPlan(planCaptor);

        assertThat(persisted.tasks().get(0).parentKey()).isNull();
        assertThat(persisted.tasks().get(1).parentKey()).isEqualTo("t1");
    }

    @Test
    void saveSuggestedProject_resolvesAnExactCaseInsensitiveCategoryMatch() {
        AssistantSuggestionWriter writer = writer(5, 40, 60);
        UUID categoryId = UUID.randomUUID();
        ProjectPlanPayload payload = new ProjectPlanPayload("Kitchen remodel", "desc", "M", "home", List.of(),
                List.of());

        ArgumentCaptor<Map<String, Object>> planCaptor = captureConvertValue();
        writer.saveSuggestedProject(run, payload, DAY, DAY.plusDays(30),
                List.of(new ProjectCategorySummary(categoryId, "Home")));
        PersistedProjectPlan persisted = reconvertPlan(planCaptor);

        assertThat(persisted.categoryId()).isEqualTo(categoryId);
        assertThat(persisted.categoryName()).isEqualTo("Home");
    }

    @Test
    void saveSuggestedProject_withNoMatchingCategory_proposesANewOneWithNullId() {
        AssistantSuggestionWriter writer = writer(5, 40, 60);
        ProjectPlanPayload payload = new ProjectPlanPayload("Kitchen remodel", "desc", "M", "Woodworking",
                List.of(), List.of());

        ArgumentCaptor<Map<String, Object>> planCaptor = captureConvertValue();
        writer.saveSuggestedProject(run, payload, DAY, DAY.plusDays(30),
                List.of(new ProjectCategorySummary(UUID.randomUUID(), "Home")));
        PersistedProjectPlan persisted = reconvertPlan(planCaptor);

        assertThat(persisted.categoryId()).isNull();
        assertThat(persisted.categoryName()).isEqualTo("Woodworking");
    }

    // --- saveSuggestedEdit ----------------------------------------------------------

    @Test
    void saveSuggestedEdit_capsTotalOperationsTruncatingTasksFirst() {
        AssistantSuggestionWriter writer = writer(5, 40, 2);
        List<TaskOperationPayload> fiveOps = IntStream.range(0, 5)
                .mapToObj(i -> new TaskOperationPayload("ADD", null, "n" + i, null, "Task " + i, null, false, DAY,
                        DAY, null))
                .toList();
        ProjectEditPayload payload = new ProjectEditPayload(null, fiveOps, List.of(), List.of());

        ArgumentCaptor<Map<String, Object>> diffCaptor = captureConvertValue();
        writer.saveSuggestedEdit(run, UUID.randomUUID(), payload, List.of());
        PersistedProjectEdit persisted = reconvertEdit(diffCaptor);

        assertThat(persisted.taskOperations()).hasSize(2);
        assertThat(persisted.dependencyOperations()).isEmpty();
        assertThat(persisted.reorderOperations()).isEmpty();
    }

    @Test
    void saveSuggestedEdit_resolvesTheProjectFieldChangesCategoryTheSameWayAPlanDoes() {
        AssistantSuggestionWriter writer = writer(5, 40, 60);
        ProjectFieldChangesPayload fieldChanges = new ProjectFieldChangesPayload(null, null, null, null, null,
                "home");
        ProjectEditPayload payload = new ProjectEditPayload(fieldChanges, List.of(), List.of(), List.of());
        UUID categoryId = UUID.randomUUID();

        ArgumentCaptor<Map<String, Object>> diffCaptor = captureConvertValue();
        writer.saveSuggestedEdit(run, UUID.randomUUID(), payload,
                List.of(new ProjectCategorySummary(categoryId, "Home")));
        PersistedProjectEdit persisted = reconvertEdit(diffCaptor);

        assertThat(persisted.projectChanges().categoryId()).isEqualTo(categoryId);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captureConvertValue() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        when(objectMapper.convertValue(captor.capture(), eq(Map.class))).thenReturn(Map.of());
        return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) captor;
    }

    @SuppressWarnings("unchecked")
    private PersistedProjectPlan reconvertPlan(ArgumentCaptor<Map<String, Object>> captor) {
        return (PersistedProjectPlan) (Object) captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private PersistedProjectEdit reconvertEdit(ArgumentCaptor<Map<String, Object>> captor) {
        return (PersistedProjectEdit) (Object) captor.getValue();
    }
}
