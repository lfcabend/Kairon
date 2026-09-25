package com.kairon.projects.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi.PlannedDependency;
import com.kairon.projects.api.ProjectsApi.PlannedTask;
import com.kairon.projects.api.ProjectsApi.ProjectPlanCommand;

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
class ProjectPlanImportServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Mock
    ProjectService projectService;

    @Mock
    ProjectTaskService taskService;

    @Mock
    TaskDependencyService dependencyService;

    ProjectPlanImportService service;

    @BeforeEach
    void setUp() {
        service = new ProjectPlanImportService(projectService, taskService, dependencyService);
        when(projectService.create(eq(USER), any())).thenReturn(project());
        when(projectService.get(USER, PROJECT_ID)).thenReturn(project());
    }

    private static ProjectView project() {
        return new ProjectView(PROJECT_ID, null, "Kitchen remodel", null, "PLANNING", null, 100, "#6366f1",
                DAY, DAY.plusDays(30), null, null, Instant.now(), Instant.now(), 0);
    }

    private static ProjectTaskView taskView(UUID id) {
        return new ProjectTaskView(id, PROJECT_ID, "Kitchen remodel", "#6366f1", null, "t", null, "TODO",
                false, DAY, DAY, null, null, 0, 100, Instant.now(), Instant.now(), 0);
    }

    private static PlannedTask task(String key, String parentKey) {
        return new PlannedTask(key, parentKey, "Task " + key, null, false, DAY, DAY.plusDays(1), null);
    }

    @Test
    void createsRootsBeforeChildrenAndMapsKeysToRealIds() {
        // Deliberately out of order: t2 (child) listed before t1 (its parent).
        ProjectPlanCommand command = new ProjectPlanCommand("Kitchen remodel", null, null, DAY, null,
                List.of(task("t2", "t1"), task("t1", null)), List.of());
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(taskService.create(eq(USER), eq(PROJECT_ID), any())).thenReturn(taskView(rootId), taskView(childId));

        service.createFromPlan(USER, command);

        ArgumentCaptor<ProjectTaskService.CreateCommand> captor =
                ArgumentCaptor.forClass(ProjectTaskService.CreateCommand.class);
        verify(taskService, times(2)).create(eq(USER), eq(PROJECT_ID), captor.capture());
        List<ProjectTaskService.CreateCommand> calls = captor.getAllValues();
        assertThat(calls.get(0).parentTaskId()).isNull();
        assertThat(calls.get(1).parentTaskId()).isEqualTo(rootId);
    }

    @Test
    void flattensAThreeLevelNestingChainToTopLevel() {
        // t1 root, t2 parent=t1, t3 parent=t2 (3rd generation — must be flattened).
        ProjectPlanCommand command = new ProjectPlanCommand("Kitchen remodel", null, null, DAY, null,
                List.of(task("t1", null), task("t2", "t1"), task("t3", "t2")), List.of());
        when(taskService.create(eq(USER), eq(PROJECT_ID), any()))
                .thenReturn(taskView(UUID.randomUUID()), taskView(UUID.randomUUID()), taskView(UUID.randomUUID()));

        service.createFromPlan(USER, command);

        ArgumentCaptor<ProjectTaskService.CreateCommand> captor =
                ArgumentCaptor.forClass(ProjectTaskService.CreateCommand.class);
        verify(taskService, times(3)).create(eq(USER), eq(PROJECT_ID), captor.capture());
        List<ProjectTaskService.CreateCommand> calls = captor.getAllValues();
        // Two top-level (t1, t3-flattened) created first, then t2 (t3's parentKey no
        // longer resolves once flattened, so it's dropped to top-level too).
        assertThat(calls.get(0).parentTaskId()).isNull();
        assertThat(calls.get(1).parentTaskId()).isNull();
        assertThat(calls.get(2).parentTaskId()).isNotNull();
    }

    @Test
    void dropsADependencyReferencingAnUnknownOrExcludedKeyWithoutThrowing() {
        ProjectPlanCommand command = new ProjectPlanCommand("Kitchen remodel", null, null, DAY, null,
                List.of(task("t1", null)),
                List.of(new PlannedDependency("t1", "ghost", "FS", 0)));
        when(taskService.create(eq(USER), eq(PROJECT_ID), any())).thenReturn(taskView(UUID.randomUUID()));

        ProjectView result = service.createFromPlan(USER, command);

        assertThat(result.id()).isEqualTo(PROJECT_ID);
        verify(dependencyService, never()).create(any(), any(), any());
    }

    @Test
    void dropsACycleCreatingEdgeInsteadOfPropagatingTheRejection() {
        ProjectPlanCommand command = new ProjectPlanCommand("Kitchen remodel", null, null, DAY, null,
                List.of(task("t1", null), task("t2", null)),
                List.of(new PlannedDependency("t1", "t2", "FS", 0)));
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(taskService.create(eq(USER), eq(PROJECT_ID), any())).thenReturn(taskView(id1), taskView(id2));
        when(dependencyService.create(eq(USER), eq(id2), any()))
                .thenThrow(ApiException.badRequest("This would create a circular dependency."));

        ProjectView result = service.createFromPlan(USER, command);

        assertThat(result.id()).isEqualTo(PROJECT_ID);
    }
}
