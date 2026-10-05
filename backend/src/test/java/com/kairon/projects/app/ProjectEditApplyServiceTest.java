package com.kairon.projects.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi.DependencyOperation;
import com.kairon.projects.api.ProjectsApi.ProjectEditCommand;
import com.kairon.projects.api.ProjectsApi.ProjectFieldChanges;
import com.kairon.projects.api.ProjectsApi.ReorderOperation;
import com.kairon.projects.api.ProjectsApi.TaskOperation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectEditApplyServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Mock
    ProjectService projectService;

    @Mock
    ProjectTaskService taskService;

    @Mock
    TaskDependencyService dependencyService;

    @Mock
    CategoryResolver categoryResolver;

    ProjectEditApplyService service;

    @BeforeEach
    void setUp() {
        service = new ProjectEditApplyService(projectService, taskService, dependencyService, categoryResolver);
        org.mockito.Mockito.lenient().when(projectService.get(USER, PROJECT_ID)).thenReturn(project());
    }

    private static ProjectView project() {
        return new ProjectView(PROJECT_ID, null, "Kitchen remodel", null, "ACTIVE", null, 100, 100, "#6366f1",
                DAY, DAY.plusDays(30), null, null, Instant.now(), Instant.now(), 0);
    }

    private static ProjectTaskView taskView(UUID id) {
        return new ProjectTaskView(id, PROJECT_ID, "Kitchen remodel", "#6366f1", null, "t", null, "TODO",
                false, DAY, DAY, null, null, 42, 100, Instant.now(), Instant.now(), 0);
    }

    private static TaskOperation add(String key, String parentRef) {
        return new TaskOperation("ADD", null, key, parentRef, "Task " + key, null, false, DAY, DAY.plusDays(1),
                null);
    }

    @Test
    void appliesProjectFieldChangesViaProjectServicePatch() {
        when(categoryResolver.resolve(USER, null, "Home")).thenReturn(UUID.randomUUID());
        ProjectEditCommand command = new ProjectEditCommand(
                new ProjectFieldChanges("Renamed", "new desc", "L", DAY, DAY.plusDays(60), null, "Home"),
                List.of(), List.of(), List.of());

        service.applyProjectEdit(USER, PROJECT_ID, command);

        ArgumentCaptor<ProjectService.PatchCommand> captor =
                ArgumentCaptor.forClass(ProjectService.PatchCommand.class);
        verify(projectService).patch(eq(USER), eq(PROJECT_ID), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Renamed");
        assertThat(captor.getValue().status()).isEqualTo("ACTIVE"); // preserved from current state
    }

    @Test
    void removesBeforeAddingAndAddsParentsBeforeNewChildren() {
        UUID removedId = UUID.randomUUID();
        UUID newRootId = UUID.randomUUID();
        UUID newChildId = UUID.randomUUID();
        // Out of order deliberately: the new child ("c1", parent "r1") is listed first.
        ProjectEditCommand command = new ProjectEditCommand(null,
                List.of(add("c1", "r1"), add("r1", null), new TaskOperation("REMOVE", removedId, null, null,
                        null, null, false, null, null, null)),
                List.of(), List.of());
        when(taskService.create(eq(USER), eq(PROJECT_ID), any())).thenReturn(taskView(newRootId),
                taskView(newChildId));

        service.applyProjectEdit(USER, PROJECT_ID, command);

        verify(taskService).delete(USER, removedId);
        ArgumentCaptor<ProjectTaskService.CreateCommand> captor =
                ArgumentCaptor.forClass(ProjectTaskService.CreateCommand.class);
        verify(taskService, times(2)).create(eq(USER), eq(PROJECT_ID), captor.capture());
        assertThat(captor.getAllValues().get(0).parentTaskId()).isNull(); // r1 first
        assertThat(captor.getAllValues().get(1).parentTaskId()).isEqualTo(newRootId); // c1 after, under r1's real id
    }

    @Test
    void updatePreservesStatusActualHoursAndProgressFromCurrentState() {
        UUID taskId = UUID.randomUUID();
        when(taskService.requireTask(USER, taskId)).thenReturn(taskView(taskId));
        ProjectEditCommand command = new ProjectEditCommand(null,
                List.of(new TaskOperation("UPDATE", taskId, null, null, "Renamed task", null, false, DAY,
                        DAY.plusDays(2), new BigDecimal("5"))),
                List.of(), List.of());

        service.applyProjectEdit(USER, PROJECT_ID, command);

        ArgumentCaptor<ProjectTaskService.PatchCommand> captor =
                ArgumentCaptor.forClass(ProjectTaskService.PatchCommand.class);
        verify(taskService).patch(eq(USER), eq(taskId), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Renamed task");
        assertThat(captor.getValue().status()).isEqualTo("TODO"); // preserved
        assertThat(captor.getValue().progressPercent()).isEqualTo(42); // preserved
    }

    @Test
    void anUpdateReferencingAStaleTaskIsDroppedNotThrown() {
        UUID taskId = UUID.randomUUID();
        when(taskService.requireTask(USER, taskId)).thenThrow(ApiException.notFound("Task not found."));
        ProjectEditCommand command = new ProjectEditCommand(null,
                List.of(new TaskOperation("UPDATE", taskId, null, null, "x", null, false, DAY, DAY, null)),
                List.of(), List.of());

        ProjectView result = service.applyProjectEdit(USER, PROJECT_ID, command);

        assertThat(result.id()).isEqualTo(PROJECT_ID);
        verify(taskService, never()).patch(any(), any(), any());
    }

    @Test
    void reorderResolvesExistingIdsAndNewKeysTogether() {
        UUID existingId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        ProjectEditCommand command = new ProjectEditCommand(null,
                List.of(add("n1", null)),
                List.of(),
                List.of(new ReorderOperation(null, List.of(existingId.toString(), "n1"))));
        when(taskService.create(eq(USER), eq(PROJECT_ID), any())).thenReturn(taskView(newId));

        service.applyProjectEdit(USER, PROJECT_ID, command);

        verify(taskService).reorder(eq(USER), eq(PROJECT_ID), eq((UUID) null), eq(List.of(existingId, newId)));
    }

    @Test
    void aReorderWithAnUnresolvableEntryIsDroppedNotThrown() {
        ProjectEditCommand command = new ProjectEditCommand(null, List.of(), List.of(),
                List.of(new ReorderOperation(null, List.of("unknown-key"))));

        service.applyProjectEdit(USER, PROJECT_ID, command);

        verify(taskService, never()).reorder(any(), any(), any(), any());
    }

    @Test
    void dependencyAddResolvesNewKeysAndDropsOnCycleRejection() {
        UUID newId = UUID.randomUUID();
        ProjectEditCommand command = new ProjectEditCommand(null,
                List.of(add("n1", null)),
                List.of(new DependencyOperation("ADD", null, "n1", "n1", "FS", 0)),
                List.of());
        when(taskService.create(eq(USER), eq(PROJECT_ID), any())).thenReturn(taskView(newId));
        when(dependencyService.create(eq(USER), eq(newId), any()))
                .thenThrow(ApiException.badRequest("A task can't depend on itself."));

        ProjectView result = service.applyProjectEdit(USER, PROJECT_ID, command);

        assertThat(result.id()).isEqualTo(PROJECT_ID);
    }

    @Test
    void dependencyRemoveIsDroppedNotThrownForAStaleId() {
        UUID dependencyId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(ApiException.notFound("Dependency not found."))
                .when(dependencyService).delete(USER, dependencyId);
        ProjectEditCommand command = new ProjectEditCommand(null, List.of(),
                List.of(new DependencyOperation("REMOVE", dependencyId, null, null, null, null)), List.of());

        ProjectView result = service.applyProjectEdit(USER, PROJECT_ID, command);

        assertThat(result.id()).isEqualTo(PROJECT_ID);
    }
}
