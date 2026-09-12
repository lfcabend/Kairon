package com.kairon.projects.repo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.identity.domain.AppUser;
import com.kairon.identity.repo.AppUserRepository;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectCategory;
import com.kairon.projects.domain.ProjectSize;
import com.kairon.projects.domain.ProjectStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
class ProjectRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    ProjectRepository repo;

    @Autowired
    ProjectCategoryRepository categories;

    @Autowired
    AppUserRepository users;

    private UUID user;
    private UUID other;

    @BeforeEach
    void createOwners() {
        user = users.save(AppUser.register("owner@example.com", "{argon2}x", "Owner", "UTC")).getId();
        other = users.save(AppUser.register("other@example.com", "{argon2}x", "Other", "UTC")).getId();
    }

    private Project project(UUID owner, UUID categoryId, ProjectStatus status, ProjectSize size, int priorityRank) {
        Project project = Project.create(owner, categoryId, "Project " + priorityRank, null, "#6366f1", size,
                priorityRank, null, null);
        project.changeStatus(status);
        return project;
    }

    @Test
    void searchFiltersByStatusCategoryAndSizeAndExcludesSoftDeletedAndOtherUsers() {
        UUID category = categories.save(ProjectCategory.create(user, "Home", "#111111", 100)).getId();
        Project active = repo.save(project(user, category, ProjectStatus.ACTIVE, ProjectSize.M, 100));
        repo.save(project(user, category, ProjectStatus.DONE, ProjectSize.M, 200));
        repo.save(project(user, null, ProjectStatus.ACTIVE, ProjectSize.S, 300));
        Project deleted = project(user, category, ProjectStatus.ACTIVE, ProjectSize.M, 400);
        deleted.softDelete(Instant.parse("2026-09-09T12:00:00Z"));
        repo.save(deleted);
        repo.save(project(other, null, ProjectStatus.ACTIVE, ProjectSize.M, 100));

        Page<Project> result = repo.search(user, ProjectStatus.ACTIVE, category, ProjectSize.M,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Project::getId).containsExactly(active.getId());
    }

    @Test
    void searchExcludingStatusHidesThatStatusRegardlessOfOtherFilters() {
        repo.save(project(user, null, ProjectStatus.ACTIVE, null, 100));
        repo.save(project(user, null, ProjectStatus.ARCHIVED, null, 200));

        Page<Project> result = repo.searchExcludingStatus(user, ProjectStatus.ARCHIVED, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Project::getStatus).containsExactly(ProjectStatus.ACTIVE);
    }

    @Test
    void rankedSetIsOrderedByPriorityRankExcludesArchivedDeletedAndOtherUsers() {
        repo.save(project(user, null, ProjectStatus.ACTIVE, null, 300));
        repo.save(project(user, null, ProjectStatus.PLANNING, null, 100));
        repo.save(project(user, null, ProjectStatus.ARCHIVED, null, 50));
        Project deleted = project(user, null, ProjectStatus.ACTIVE, null, 20);
        deleted.softDelete(Instant.parse("2026-09-09T12:00:00Z"));
        repo.save(deleted);
        repo.save(project(other, null, ProjectStatus.ACTIVE, null, 10));

        List<Project> ranked = repo.findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc(
                user, ProjectStatus.ARCHIVED);

        assertThat(ranked).extracting(Project::getPriorityRank).containsExactly(100, 300);
    }

    @Test
    void findByIdScopedToUserHonoursOwnershipAndSoftDelete() {
        Project mine = repo.save(project(user, null, ProjectStatus.PLANNING, null, 100));

        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), user)).isPresent();
        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), other)).isEmpty();

        mine.softDelete(Instant.parse("2026-09-09T15:00:00Z"));
        repo.save(mine);
        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), user)).isEmpty();
    }
}
