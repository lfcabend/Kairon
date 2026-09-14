package com.kairon.projects.repo;

import java.util.List;
import java.util.UUID;

import com.kairon.identity.domain.AppUser;
import com.kairon.identity.repo.AppUserRepository;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectTask;
import com.kairon.projects.domain.TaskDependency;
import com.kairon.projects.domain.TaskDependencyType;

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
class TaskDependencyRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TaskDependencyRepository repo;

    @Autowired
    ProjectTaskRepository tasks;

    @Autowired
    ProjectRepository projects;

    @Autowired
    AppUserRepository users;

    private UUID projectId;
    private UUID otherProjectId;

    @BeforeEach
    void createOwnerAndProjects() {
        UUID user = users.save(AppUser.register("owner@example.com", "{argon2}x", "Owner", "UTC")).getId();
        projectId = projects.save(Project.create(user, null, "Project", null, "#6366f1", null, 100, null, null))
                .getId();
        otherProjectId = projects
                .save(Project.create(user, null, "Other project", null, "#6366f1", null, 200, null, null))
                .getId();
    }

    private UUID task(UUID projectId, String name) {
        return tasks.save(ProjectTask.create(projectId, null, name, null, 100)).getId();
    }

    @Test
    void findByProjectIdScopesToTasksInThatProjectViaPredecessor() {
        UUID a = task(projectId, "a");
        UUID b = task(projectId, "b");
        UUID otherA = task(otherProjectId, "other-a");
        UUID otherB = task(otherProjectId, "other-b");
        repo.save(TaskDependency.create(a, b, TaskDependencyType.FS, 0));
        repo.save(TaskDependency.create(otherA, otherB, TaskDependencyType.FS, 0));

        List<TaskDependency> edges = repo.findByProjectId(projectId);

        assertThat(edges).extracting(TaskDependency::getPredecessorId).containsExactly(a);
        assertThat(edges).extracting(TaskDependency::getSuccessorId).containsExactly(b);
    }

    @Test
    void findByPredecessorIdAndSuccessorIdFindsExistingEdgeOnly() {
        UUID a = task(projectId, "a");
        UUID b = task(projectId, "b");
        repo.save(TaskDependency.create(a, b, TaskDependencyType.FS, 0));

        assertThat(repo.findByPredecessorIdAndSuccessorId(a, b)).isPresent();
        assertThat(repo.findByPredecessorIdAndSuccessorId(b, a)).isEmpty();
    }

    @Test
    void deleteAllForTaskRemovesEdgesOnEitherSide() {
        UUID a = task(projectId, "a");
        UUID b = task(projectId, "b");
        UUID c = task(projectId, "c");
        repo.save(TaskDependency.create(a, b, TaskDependencyType.FS, 0));
        repo.save(TaskDependency.create(b, c, TaskDependencyType.FS, 0));

        repo.deleteAllForTask(b);

        assertThat(repo.findByProjectId(projectId)).isEmpty();
    }
}
