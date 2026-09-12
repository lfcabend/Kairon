package com.kairon.projects.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.identity.domain.AppUser;
import com.kairon.identity.repo.AppUserRepository;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectTask;
import com.kairon.projects.domain.ProjectTaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** Repository slice against a real PostgreSQL (docs/testing approach: never H2). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class ProjectTaskRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 9);

    @Autowired
    ProjectTaskRepository repo;

    @Autowired
    ProjectRepository projects;

    @Autowired
    AppUserRepository users;

    private UUID user;
    private UUID otherUser;
    private UUID projectId;

    @BeforeEach
    void createOwnerAndProject() {
        user = users.save(AppUser.register("owner@example.com", "{argon2}x", "Owner", "UTC")).getId();
        otherUser = users.save(AppUser.register("other@example.com", "{argon2}x", "Other", "UTC")).getId();
        projectId = projects.save(Project.create(user, null, "Project", null, "#6366f1", null, 100, null, null))
                .getId();
    }

    private ProjectTask task(UUID parentTaskId, int position, String name) {
        return ProjectTask.create(projectId, parentTaskId, name, null, position);
    }

    @Test
    void siblingGroupQueryOrdersByPositionAndScopesToProjectAndParent() {
        ProjectTask parent = repo.save(task(null, 100, "parent"));
        repo.save(task(parent.getId(), 200, "child-b"));
        repo.save(task(parent.getId(), 100, "child-a"));
        repo.save(task(null, 200, "top-level-2"));

        List<ProjectTask> topLevel = repo
                .findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(projectId, null);
        List<ProjectTask> children = repo
                .findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(projectId, parent.getId());

        assertThat(topLevel).extracting(ProjectTask::getName).containsExactly("parent", "top-level-2");
        assertThat(children).extracting(ProjectTask::getName).containsExactly("child-a", "child-b");
    }

    @Test
    void existsByParentTaskIdIgnoresSoftDeletedChildren() {
        ProjectTask parent = repo.save(task(null, 100, "parent"));
        assertThat(repo.existsByParentTaskIdAndDeletedAtIsNull(parent.getId())).isFalse();

        ProjectTask child = repo.save(task(parent.getId(), 100, "child"));
        assertThat(repo.existsByParentTaskIdAndDeletedAtIsNull(parent.getId())).isTrue();

        child.softDelete(Instant.parse("2026-09-09T12:00:00Z"));
        repo.save(child);
        assertThat(repo.existsByParentTaskIdAndDeletedAtIsNull(parent.getId())).isFalse();
    }

    @Test
    void findByIdAndDeletedAtIsNullHonoursSoftDeleteButNotOwnership() {
        ProjectTask mine = repo.save(task(null, 100, "mine"));

        assertThat(repo.findByIdAndDeletedAtIsNull(mine.getId())).isPresent();

        mine.softDelete(Instant.parse("2026-09-09T12:00:00Z"));
        repo.save(mine);
        assertThat(repo.findByIdAndDeletedAtIsNull(mine.getId())).isEmpty();
    }

    @Test
    void dueOrOverdueMatchesWindowContainsDayOrOverdueNotDoneScopedByOwningProjectsUser() {
        ProjectTask withinWindow = task(null, 100, "within");
        withinWindow.reschedule(DAY.minusDays(1), DAY.plusDays(1));
        repo.save(withinWindow);

        ProjectTask overdueOpen = task(null, 200, "overdue-open");
        overdueOpen.reschedule(DAY.minusDays(5), DAY.minusDays(3));
        repo.save(overdueOpen);

        ProjectTask overdueDone = task(null, 300, "overdue-done");
        overdueDone.reschedule(DAY.minusDays(5), DAY.minusDays(3));
        overdueDone.changeStatus(ProjectTaskStatus.DONE);
        repo.save(overdueDone);

        ProjectTask future = task(null, 400, "future");
        future.reschedule(DAY.plusDays(2), DAY.plusDays(5));
        repo.save(future);

        UUID otherProjectId = projects.save(
                Project.create(otherUser, null, "Other project", null, "#6366f1", null, 100, null, null)).getId();
        ProjectTask notMine = ProjectTask.create(otherProjectId, null, "not mine", null, 100);
        notMine.reschedule(DAY.minusDays(1), DAY.plusDays(1));
        repo.save(notMine);

        List<ProjectTask> due = repo.findDueOrOverdue(user, DAY);

        assertThat(due).extracting(ProjectTask::getName).containsExactly("overdue-open", "within");
    }
}
