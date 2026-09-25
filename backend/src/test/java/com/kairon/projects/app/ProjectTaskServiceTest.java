package com.kairon.projects.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.app.ProjectTaskService.CreateCommand;
import com.kairon.projects.app.ProjectTaskService.PatchCommand;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectTask;
import com.kairon.projects.repo.ProjectRepository;
import com.kairon.projects.repo.ProjectTaskRepository;
import com.kairon.projects.repo.TaskDependencyRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000e1"));

    @Mock
    ProjectTaskRepository tasks;

    @Mock
    ProjectRepository projects;

    @Mock
    TaskDependencyRepository dependencies;

    ProjectTaskService service;

    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskService(tasks, projects, dependencies, new ProjectsProperties(0),
                Clock.fixed(NOW, ZoneOffset.UTC), null);
        project = Project.create(USER.value(), null, "Project", null, "#6366f1", null, 100, null, null);
        org.mockito.Mockito.lenient().when(projects.findByIdAndUserIdAndDeletedAtIsNull(project.getId(), USER.value()))
                .thenReturn(Optional.of(project));
    }

    private ProjectTask taskAt(UUID parentTaskId, int position) {
        return ProjectTask.create(project.getId(), parentTaskId, "t@" + position, null, position);
    }

    @Test
    void createAppendsAtMaxPositionPlus100PerSiblingGroup() {
        when(tasks.findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(project.getId(), null))
                .thenReturn(List.of(taskAt(null, 100), taskAt(null, 250)));
        when(tasks.save(any(ProjectTask.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectTaskView created = service.create(USER, project.getId(),
                new CreateCommand("New task", null, null, null, null, null, false));

        assertThat(created.position()).isEqualTo(350);
        assertThat(created.status()).isEqualTo("TODO");
        assertThat(created.projectName()).isEqualTo("Project");
    }

    @Test
    void assigningASubtaskAsAParentIs400() {
        ProjectTask top = taskAt(null, 100);
        ProjectTask sub = taskAt(top.getId(), 100);
        when(tasks.findByIdAndProjectIdAndDeletedAtIsNull(sub.getId(), project.getId()))
                .thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.create(USER, project.getId(),
                new CreateCommand("x", null, sub.getId(), null, null, null, false)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void aTaskWithChildrenCannotBeNestedUnderAnotherTask() {
        ProjectTask parentCandidate = taskAt(null, 100);
        ProjectTask taskWithChildren = taskAt(null, 200);
        when(tasks.findByIdAndDeletedAtIsNull(taskWithChildren.getId())).thenReturn(Optional.of(taskWithChildren));
        when(tasks.findByIdAndProjectIdAndDeletedAtIsNull(parentCandidate.getId(), project.getId()))
                .thenReturn(Optional.of(parentCandidate));
        when(tasks.existsByParentTaskIdAndDeletedAtIsNull(taskWithChildren.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.patch(USER, taskWithChildren.getId(), new PatchCommand(
                "x", null, "TODO", parentCandidate.getId(), null, null, null, null, 0, false, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void validReparentSucceeds() {
        ProjectTask newParent = taskAt(null, 100);
        ProjectTask task = taskAt(null, 200);
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));
        when(tasks.findByIdAndProjectIdAndDeletedAtIsNull(newParent.getId(), project.getId()))
                .thenReturn(Optional.of(newParent));
        when(tasks.existsByParentTaskIdAndDeletedAtIsNull(task.getId())).thenReturn(false);

        ProjectTaskView patched = service.patch(USER, task.getId(), new PatchCommand(
                "x", null, "TODO", newParent.getId(), null, null, null, null, 0, false, null));

        assertThat(patched.parentTaskId()).isEqualTo(newParent.getId());
    }

    @Test
    void patchWithNullParentTaskIdMovesATaskBackToTopLevel() {
        ProjectTask child = taskAt(UUID.randomUUID(), 100);
        when(tasks.findByIdAndDeletedAtIsNull(child.getId())).thenReturn(Optional.of(child));

        ProjectTaskView patched = service.patch(USER, child.getId(), new PatchCommand(
                "x", null, "TODO", null, null, null, null, null, 0, false, null));

        assertThat(patched.parentTaskId()).isNull();
    }

    @Test
    void reorderRewritesPositionsWithinASiblingGroupAndRejectsAMismatch() {
        ProjectTask a = taskAt(null, 100);
        ProjectTask b = taskAt(null, 200);
        when(tasks.findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(project.getId(), null))
                .thenReturn(List.of(a, b));

        List<ProjectTaskView> result = service.reorder(USER, project.getId(), null, List.of(b.getId(), a.getId()));
        assertThat(result).extracting(ProjectTaskView::position).containsExactly(100, 200);

        assertThatThrownBy(() -> service.reorder(USER, project.getId(), null, List.of(a.getId())))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void progressPercentOutsideRangeIsRejected() {
        ProjectTask task = taskAt(null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.patch(USER, task.getId(), new PatchCommand(
                "x", null, "TODO", null, null, null, null, null, 150, false, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void markMilestoneForcesEqualPlannedStartAndEnd() {
        ProjectTask task = taskAt(null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));

        LocalDate date = LocalDate.of(2026, 9, 20);
        ProjectTaskView patched = service.patch(USER, task.getId(), new PatchCommand(
                "x", null, "TODO", null, date, null, null, null, 0, true, null));

        assertThat(patched.isMilestone()).isTrue();
        assertThat(patched.plannedStart()).isEqualTo(date);
        assertThat(patched.plannedEnd()).isEqualTo(date);
    }

    @Test
    void deleteCascadesToItsOwnSubtasksOnly() {
        ProjectTask task = taskAt(null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));
        ProjectTask child = taskAt(task.getId(), 100);
        when(tasks.findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(project.getId(), task.getId()))
                .thenReturn(List.of(child));

        service.delete(USER, task.getId());

        assertThat(task.isDeleted()).isTrue();
        assertThat(child.isDeleted()).isTrue();
        org.mockito.Mockito.verify(dependencies).deleteAllForTask(task.getId());
        org.mockito.Mockito.verify(dependencies).deleteAllForTask(child.getId());
    }

    @Test
    void aTaskWhoseProjectBelongsToAnotherUserIs404() {
        UUID taskId = UUID.randomUUID();
        Project foreignProject = Project.create(UUID.randomUUID(), null, "Foreign", null, "#6366f1", null, 100,
                null, null);
        ProjectTask task = ProjectTask.create(foreignProject.getId(), null, "t", null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(task));
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(foreignProject.getId(), USER.value()))
                .thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.delete(USER, taskId));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void requireTaskReturnsAViewForAnOwnedTask() {
        ProjectTask task = taskAt(null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));

        ProjectTaskView view = service.requireTask(USER, task.getId());

        assertThat(view.id()).isEqualTo(task.getId());
        assertThat(view.projectName()).isEqualTo("Project");
    }

    @Test
    void requireTaskIs404ForAMissingTask() {
        UUID taskId = UUID.randomUUID();
        when(tasks.findByIdAndDeletedAtIsNull(taskId)).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.requireTask(USER, taskId));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void requireTaskIs404ForATaskBelongingToAnotherUsersProject() {
        Project foreignProject = Project.create(UUID.randomUUID(), null, "Foreign", null, "#6366f1", null, 100,
                null, null);
        ProjectTask task = ProjectTask.create(foreignProject.getId(), null, "t", null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(foreignProject.getId(), USER.value()))
                .thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.requireTask(USER, task.getId()));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void completeTaskIfPresentMarksAnOwnedNonDoneTaskDone() {
        ProjectTask task = taskAt(null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));

        service.completeTaskIfPresent(USER, task.getId());

        assertThat(task.getStatus().name()).isEqualTo("DONE");
    }

    @Test
    void completeTaskIfPresentNoOpsForAMissingTask() {
        UUID taskId = UUID.randomUUID();
        when(tasks.findByIdAndDeletedAtIsNull(taskId)).thenReturn(Optional.empty());

        assertThatCode(() -> service.completeTaskIfPresent(USER, taskId)).doesNotThrowAnyException();
    }

    @Test
    void completeTaskIfPresentNoOpsForAForeignTask() {
        Project foreignProject = Project.create(UUID.randomUUID(), null, "Foreign", null, "#6366f1", null, 100,
                null, null);
        ProjectTask task = ProjectTask.create(foreignProject.getId(), null, "t", null, 100);
        when(tasks.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(foreignProject.getId(), USER.value()))
                .thenReturn(Optional.empty());

        service.completeTaskIfPresent(USER, task.getId());

        assertThat(task.getStatus().name()).isNotEqualTo("DONE");
    }
}
