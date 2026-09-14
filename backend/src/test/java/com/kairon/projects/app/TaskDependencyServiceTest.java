package com.kairon.projects.app;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.app.TaskDependencyService.CreateCommand;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectTask;
import com.kairon.projects.domain.TaskDependency;
import com.kairon.projects.domain.TaskDependencyType;
import com.kairon.projects.repo.ProjectRepository;
import com.kairon.projects.repo.ProjectTaskRepository;
import com.kairon.projects.repo.TaskDependencyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDependencyServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));

    @Mock
    TaskDependencyRepository dependencies;

    @Mock
    ProjectTaskRepository tasks;

    @Mock
    ProjectRepository projects;

    TaskDependencyService service;

    private Project project;

    @BeforeEach
    void setUp() {
        service = new TaskDependencyService(dependencies, tasks, projects);
        project = Project.create(USER.value(), null, "Project", null, "#6366f1", null, 100, null, null);
        lenient().when(projects.findByIdAndUserIdAndDeletedAtIsNull(project.getId(), USER.value()))
                .thenReturn(Optional.of(project));
    }

    private ProjectTask taskAt(String name) {
        return ProjectTask.create(project.getId(), null, name, null, 100);
    }

    @Test
    void selfDependencyIs400() {
        ProjectTask task = taskAt("a");
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.create(USER, task.getId(), new CreateCommand(task.getId(), null, null)));

        assertThat(ex.getStatus().value()).isEqualTo(400);
    }

    @Test
    void crossProjectPredecessorIs400() {
        ProjectTask successor = taskAt("successor");
        UUID predecessorId = UUID.randomUUID();
        when(tasks.findByIdAndDeletedAtIsNull(successor.getId())).thenReturn(Optional.of(successor));
        when(tasks.findByIdAndProjectIdAndDeletedAtIsNull(predecessorId, project.getId()))
                .thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.create(USER, successor.getId(), new CreateCommand(predecessorId, null, null)));

        assertThat(ex.getStatus().value()).isEqualTo(400);
    }

    @Test
    void duplicateEdgeIs409() {
        ProjectTask predecessor = taskAt("predecessor");
        ProjectTask successor = taskAt("successor");
        when(tasks.findByIdAndDeletedAtIsNull(successor.getId())).thenReturn(Optional.of(successor));
        when(tasks.findByIdAndProjectIdAndDeletedAtIsNull(predecessor.getId(), project.getId()))
                .thenReturn(Optional.of(predecessor));
        when(dependencies.findByPredecessorIdAndSuccessorId(predecessor.getId(), successor.getId()))
                .thenReturn(Optional.of(TaskDependency.create(predecessor.getId(), successor.getId(),
                        TaskDependencyType.FS, 0)));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.create(USER, successor.getId(), new CreateCommand(predecessor.getId(), null, null)));

        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    void aThreeEdgeCycleIsRejected() {
        // Existing edges A->B, B->C; attempting C->A should be rejected as a cycle.
        ProjectTask a = taskAt("a");
        ProjectTask b = taskAt("b");
        ProjectTask c = taskAt("c");
        when(tasks.findByIdAndDeletedAtIsNull(a.getId())).thenReturn(Optional.of(a));
        when(tasks.findByIdAndProjectIdAndDeletedAtIsNull(c.getId(), project.getId())).thenReturn(Optional.of(c));
        when(dependencies.findByPredecessorIdAndSuccessorId(c.getId(), a.getId())).thenReturn(Optional.empty());
        when(dependencies.findByProjectId(project.getId())).thenReturn(List.of(
                TaskDependency.create(a.getId(), b.getId(), TaskDependencyType.FS, 0),
                TaskDependency.create(b.getId(), c.getId(), TaskDependencyType.FS, 0)));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.create(USER, a.getId(), new CreateCommand(c.getId(), null, null)));

        assertThat(ex.getStatus().value()).isEqualTo(400);
    }

    @Test
    void aValidEdgeCreates() {
        ProjectTask predecessor = taskAt("predecessor");
        ProjectTask successor = taskAt("successor");
        when(tasks.findByIdAndDeletedAtIsNull(successor.getId())).thenReturn(Optional.of(successor));
        when(tasks.findByIdAndProjectIdAndDeletedAtIsNull(predecessor.getId(), project.getId()))
                .thenReturn(Optional.of(predecessor));
        when(dependencies.findByPredecessorIdAndSuccessorId(predecessor.getId(), successor.getId()))
                .thenReturn(Optional.empty());
        when(dependencies.findByProjectId(project.getId())).thenReturn(List.of());
        when(dependencies.save(any(TaskDependency.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tasks.findByProjectIdAndDeletedAtIsNull(project.getId())).thenReturn(List.of(predecessor, successor));

        TaskDependencyView created = service.create(USER, successor.getId(),
                new CreateCommand(predecessor.getId(), null, null));

        assertThat(created.predecessorId()).isEqualTo(predecessor.getId());
        assertThat(created.successorId()).isEqualTo(successor.getId());
        assertThat(created.type()).isEqualTo("FS");
        assertThat(created.lagDays()).isEqualTo(0);
    }

    @Test
    void listComputesFsViolationWhenSuccessorStartsBeforePredecessorEnds() {
        ProjectTask predecessor = taskAt("predecessor");
        predecessor.reschedule(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10));
        ProjectTask successor = taskAt("successor");
        successor.reschedule(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 15));
        when(tasks.findByProjectIdAndDeletedAtIsNull(project.getId())).thenReturn(List.of(predecessor, successor));
        when(dependencies.findByProjectId(project.getId())).thenReturn(
                List.of(TaskDependency.create(predecessor.getId(), successor.getId(), TaskDependencyType.FS, 0)));

        List<TaskDependencyView> views = service.list(USER, project.getId());

        assertThat(views).singleElement().satisfies(v -> assertThat(v.violatesConstraint()).isTrue());
    }

    @Test
    void deleteOnAForeignTaskIs404() {
        UUID dependencyId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();
        TaskDependency dependency = TaskDependency.create(UUID.randomUUID(), successorId, TaskDependencyType.FS, 0);
        when(dependencies.findById(dependencyId)).thenReturn(Optional.of(dependency));
        ProjectTask foreignSuccessor = ProjectTask.create(UUID.randomUUID(), null, "s", null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(successorId)).thenReturn(Optional.of(foreignSuccessor));
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(foreignSuccessor.getProjectId(), USER.value()))
                .thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.delete(USER, dependencyId));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }
}
