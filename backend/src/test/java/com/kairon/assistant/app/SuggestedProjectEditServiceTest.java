package com.kairon.assistant.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedProjectEdit;
import com.kairon.assistant.llm.DependencyOperationPayload;
import com.kairon.assistant.llm.ReorderOperationPayload;
import com.kairon.assistant.llm.TaskOperationPayload;
import com.kairon.assistant.repo.AssistantSuggestedProjectEditRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectsApi.ProjectEditCommand;

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

@ExtendWith(MockitoExtension.class)
class SuggestedProjectEditServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));
    private static final UUID RUN_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Mock
    AssistantSuggestedProjectEditRepository suggestedProjectEdits;

    @Mock
    ProjectsApi projectsApi;

    @Mock
    ObjectMapper objectMapper;

    SuggestedProjectEditService service;

    private PersistedProjectEdit diff;

    @BeforeEach
    void setUp() {
        service = new SuggestedProjectEditService(suggestedProjectEdits, projectsApi, objectMapper);
        diff = new PersistedProjectEdit(null, List.of(), List.of(), List.of());
        org.mockito.Mockito.lenient().when(objectMapper.convertValue(any(), eq(PersistedProjectEdit.class)))
                .thenAnswer(inv -> diff);
    }

    private AssistantSuggestedProjectEdit proposed() {
        return AssistantSuggestedProjectEdit.propose(RUN_ID, USER.value(), PROJECT_ID, java.util.Map.of());
    }

    @Test
    void acceptBuildsACommandFromTheStoredDiffAndAppliesIt() {
        UUID existingTaskId = UUID.randomUUID();
        diff = new PersistedProjectEdit(null,
                List.of(new TaskOperationPayload("ADD", null, "n1", null, "Inspection", null, false, DAY,
                        DAY.plusDays(1), null)),
                List.of(new DependencyOperationPayload("ADD", null, existingTaskId.toString(), "n1", "FS", 0)),
                List.of());
        AssistantSuggestedProjectEdit row = proposed();
        when(suggestedProjectEdits.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));
        ProjectView updated = projectView();
        when(projectsApi.applyProjectEdit(eq(USER), eq(PROJECT_ID), any())).thenReturn(updated);

        ProjectView result = service.accept(USER, row.getId(), List.of());

        assertThat(result).isEqualTo(updated);
        assertThat(row.getStatus().name()).isEqualTo("ACCEPTED");
        ArgumentCaptor<ProjectEditCommand> captor = ArgumentCaptor.forClass(ProjectEditCommand.class);
        verify(projectsApi).applyProjectEdit(eq(USER), eq(PROJECT_ID), captor.capture());
        assertThat(captor.getValue().taskOperations()).hasSize(1);
        assertThat(captor.getValue().dependencyOperations()).hasSize(1);
        assertThat(captor.getValue().dependencyOperations().get(0).predecessorRef()).isEqualTo(existingTaskId.toString());
        assertThat(captor.getValue().dependencyOperations().get(0).successorRef()).isEqualTo("n1");
    }

    @Test
    void excludingANewTaskCascadesToOperationsReferencingItsKey() {
        diff = new PersistedProjectEdit(null,
                List.of(
                        new TaskOperationPayload("ADD", null, "n1", null, "Inspection", null, false, DAY,
                                DAY.plusDays(1), null),
                        new TaskOperationPayload("ADD", null, "n2", "n1", "Sub-inspection", null, false, DAY,
                                DAY, null)),
                List.of(new DependencyOperationPayload("ADD", null, "n1", "n2", "FS", 0)),
                List.of(new ReorderOperationPayload(null, List.of("n1"))));
        AssistantSuggestedProjectEdit row = proposed();
        when(suggestedProjectEdits.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));
        when(projectsApi.applyProjectEdit(eq(USER), eq(PROJECT_ID), any())).thenReturn(projectView());

        // Exclude the first task op (index 0, "task:0") -> its key "n1" cascades to
        // the second (new) task that parents under it, the dependency referencing
        // it, and the reorder entry listing it.
        service.accept(USER, row.getId(), List.of("task:0"));

        ArgumentCaptor<ProjectEditCommand> captor = ArgumentCaptor.forClass(ProjectEditCommand.class);
        verify(projectsApi).applyProjectEdit(eq(USER), eq(PROJECT_ID), captor.capture());
        assertThat(captor.getValue().taskOperations()).isEmpty();
        assertThat(captor.getValue().dependencyOperations()).isEmpty();
        assertThat(captor.getValue().reorderOperations()).isEmpty();
    }

    @Test
    void aMatchedExistingCategoryPassesItsIdThroughAndNoNewCategoryName() {
        UUID categoryId = UUID.randomUUID();
        diff = new PersistedProjectEdit(
                new PersistedProjectEdit.PersistedProjectFieldChanges(null, null, null, null, null, categoryId,
                        "Home"),
                List.of(), List.of(), List.of());
        AssistantSuggestedProjectEdit row = proposed();
        when(suggestedProjectEdits.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));
        when(projectsApi.applyProjectEdit(eq(USER), eq(PROJECT_ID), any())).thenReturn(projectView());

        service.accept(USER, row.getId(), List.of());

        ArgumentCaptor<ProjectEditCommand> captor = ArgumentCaptor.forClass(ProjectEditCommand.class);
        verify(projectsApi).applyProjectEdit(eq(USER), eq(PROJECT_ID), captor.capture());
        assertThat(captor.getValue().projectChanges().categoryId()).isEqualTo(categoryId);
        assertThat(captor.getValue().projectChanges().newCategoryName()).isNull();
    }

    @Test
    void anUnmatchedProposedCategoryPassesItAsANewCategoryName() {
        diff = new PersistedProjectEdit(
                new PersistedProjectEdit.PersistedProjectFieldChanges(null, null, null, null, null, null, "Home"),
                List.of(), List.of(), List.of());
        AssistantSuggestedProjectEdit row = proposed();
        when(suggestedProjectEdits.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));
        when(projectsApi.applyProjectEdit(eq(USER), eq(PROJECT_ID), any())).thenReturn(projectView());

        service.accept(USER, row.getId(), List.of());

        ArgumentCaptor<ProjectEditCommand> captor = ArgumentCaptor.forClass(ProjectEditCommand.class);
        verify(projectsApi).applyProjectEdit(eq(USER), eq(PROJECT_ID), captor.capture());
        assertThat(captor.getValue().projectChanges().categoryId()).isNull();
        assertThat(captor.getValue().projectChanges().newCategoryName()).isEqualTo("Home");
    }

    @Test
    void acceptOnAnAlreadyResolvedDiffIsAConflict() {
        AssistantSuggestedProjectEdit row = proposed();
        row.dismiss();
        when(suggestedProjectEdits.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.accept(USER, row.getId(), List.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(409));
        verify(projectsApi, never()).applyProjectEdit(any(), any(), any());
    }

    @Test
    void dismissFlipsStatusWithoutTouchingProjectsApi() {
        AssistantSuggestedProjectEdit row = proposed();
        when(suggestedProjectEdits.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));

        AssistantSuggestedProjectEditView view = service.dismiss(USER, row.getId());

        assertThat(view.status()).isEqualTo("DISMISSED");
        verify(projectsApi, never()).applyProjectEdit(any(), any(), any());
    }

    @Test
    void missingOrForeignDiffIs404() {
        UUID id = UUID.randomUUID();
        when(suggestedProjectEdits.findByIdAndUserId(id, USER.value())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss(USER, id))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    private static ProjectView projectView() {
        return new ProjectView(PROJECT_ID, null, "Kitchen remodel", null, "ACTIVE", null, 100, 100, "#6366f1",
                DAY, DAY.plusDays(30), null, null, Instant.now(), Instant.now(), 0);
    }
}
