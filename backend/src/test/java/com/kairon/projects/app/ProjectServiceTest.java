package com.kairon.projects.app;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.app.ProjectService.CreateCommand;
import com.kairon.projects.app.ProjectService.PatchCommand;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectSize;
import com.kairon.projects.domain.ProjectStatus;
import com.kairon.projects.domain.ProjectTask;
import com.kairon.projects.repo.ProjectCategoryRepository;
import com.kairon.projects.repo.ProjectRepository;
import com.kairon.projects.repo.ProjectTaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000d1"));

    @Mock
    ProjectRepository projects;

    @Mock
    ProjectCategoryRepository categories;

    @Mock
    ProjectTaskRepository tasks;

    @Mock
    com.kairon.projects.repo.TaskDependencyRepository dependencies;

    ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projects, categories, tasks, dependencies, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Project rankedProject(int priorityRank) {
        Project p = Project.create(USER.value(), null, "p@" + priorityRank, null, "#6366f1", null,
                priorityRank, null, null);
        return p;
    }

    @Test
    void createDefaultsUnsizedAndAppendsAtMaxPriorityRankPlus100() {
        when(projects.findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc(
                USER.value(), ProjectStatus.ARCHIVED))
                .thenReturn(List.of(rankedProject(100), rankedProject(250)));
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectView created = service.create(USER, new CreateCommand(null, "New project", null, null, null, null, null));

        assertThat(created.priorityRank()).isEqualTo(350);
        assertThat(created.size()).isNull();
        assertThat(created.status()).isEqualTo("PLANNING");
        assertThat(created.color()).isEqualTo("#6366f1");
    }

    @Test
    void createOnAnEmptySetStartsPriorityRankAt100() {
        when(projects.findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc(
                USER.value(), ProjectStatus.ARCHIVED)).thenReturn(List.of());
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectView created = service.create(USER, new CreateCommand(null, "First", null, null, null, null, null));

        assertThat(created.priorityRank()).isEqualTo(100);
    }

    @Test
    void createWithAForeignCategoryIs404() {
        UUID categoryId = UUID.randomUUID();
        when(categories.findByIdAndUserId(categoryId, USER.value())).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.create(USER,
                new CreateCommand(categoryId, "New", null, null, null, null, null)));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void createWithAnInvalidSizeIs400() {
        assertThatThrownBy(() -> service.create(USER,
                new CreateCommand(null, "New", null, "HUGE", null, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void aForeignOrMissingRowIs404() {
        UUID id = UUID.randomUUID();
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(id, USER.value())).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.get(USER, id));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void staleExpectedVersionIs409() {
        Project project = rankedProject(100); // version 0
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(project.getId(), USER.value()))
                .thenReturn(Optional.of(project));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.patch(USER, project.getId(),
                new PatchCommand(null, "x", null, "PLANNING", null, null, null, null, null, null, 7L)));

        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    void patchNeverChangesPriorityRankEvenIfTheDomainMethodIsNeverInvoked() {
        Project project = rankedProject(500);
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(project.getId(), USER.value()))
                .thenReturn(Optional.of(project));

        ProjectView patched = service.patch(USER, project.getId(), new PatchCommand(
                null, "Renamed", null, "ACTIVE", null, null, null, null, null, null, null));

        assertThat(patched.priorityRank()).isEqualTo(500);
        assertThat(patched.name()).isEqualTo("Renamed");
        assertThat(patched.status()).isEqualTo("ACTIVE");
    }

    @Test
    void patchWithCategoryIdAndSizeNullClearsThem() {
        Project project = Project.create(USER.value(), UUID.randomUUID(), "p", null, "#111111",
                ProjectSize.M, 100, null, null);
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(project.getId(), USER.value()))
                .thenReturn(Optional.of(project));

        ProjectView patched = service.patch(USER, project.getId(), new PatchCommand(
                null, "p", null, "PLANNING", null, null, null, null, null, null, null));

        assertThat(patched.categoryId()).isNull();
        assertThat(patched.size()).isNull();
    }

    @Test
    void reorderRewritesPriorityRankTo100200300InTheGivenOrder() {
        Project a = rankedProject(100);
        Project b = rankedProject(200);
        Project c = rankedProject(300);
        when(projects.findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc(
                USER.value(), ProjectStatus.ARCHIVED)).thenReturn(List.of(a, b, c));

        List<ProjectView> result = service.reorder(USER, List.of(c.getId(), a.getId(), b.getId()));

        assertThat(result).extracting(ProjectView::id).containsExactly(c.getId(), a.getId(), b.getId());
        assertThat(result).extracting(ProjectView::priorityRank).containsExactly(100, 200, 300);
    }

    @Test
    void reorderRejectsAMismatchedIdSetWith400() {
        Project a = rankedProject(100);
        when(projects.findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc(
                USER.value(), ProjectStatus.ARCHIVED)).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.reorder(USER, List.of(a.getId(), UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void deleteCascadesSoftDeleteToItsTasks() {
        Project project = rankedProject(100);
        when(projects.findByIdAndUserIdAndDeletedAtIsNull(project.getId(), USER.value()))
                .thenReturn(Optional.of(project));
        ProjectTask t1 = ProjectTask.create(project.getId(), null, "t1", null, 100);
        ProjectTask t2 = ProjectTask.create(project.getId(), null, "t2", null, 200);
        when(tasks.findByProjectIdAndDeletedAtIsNull(project.getId())).thenReturn(List.of(t1, t2));

        service.delete(USER, project.getId());

        assertThat(project.isDeleted()).isTrue();
        assertThat(t1.isDeleted()).isTrue();
        assertThat(t2.isDeleted()).isTrue();
        org.mockito.Mockito.verify(dependencies).deleteAllForTask(t1.getId());
        org.mockito.Mockito.verify(dependencies).deleteAllForTask(t2.getId());
    }

    @Test
    void listWithNoStatusFilterExcludesArchivedByDefault() {
        Project active = rankedProject(100);
        when(projects.searchExcludingStatus(eq(USER.value()), eq(ProjectStatus.ARCHIVED), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(active)));

        var page = service.list(USER, null, null, null, false, 0, 20, null);

        assertThat(page.content()).extracting(ProjectView::id).containsExactly(active.getId());
    }

    @Test
    void listWithIncludeArchivedTrueUsesThePlainSearch() {
        Project archived = rankedProject(100);
        when(projects.search(eq(USER.value()), isNull(), isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(archived)));

        var page = service.list(USER, null, null, null, true, 0, 20, null);

        assertThat(page.content()).extracting(ProjectView::id).containsExactly(archived.getId());
    }
}
