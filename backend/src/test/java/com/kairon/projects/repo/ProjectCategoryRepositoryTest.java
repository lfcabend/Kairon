package com.kairon.projects.repo;

import java.util.List;
import java.util.UUID;

import com.kairon.identity.domain.AppUser;
import com.kairon.identity.repo.AppUserRepository;
import com.kairon.projects.domain.ProjectCategory;

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
class ProjectCategoryRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    ProjectCategoryRepository repo;

    @Autowired
    AppUserRepository users;

    private UUID user;
    private UUID other;

    @BeforeEach
    void createOwners() {
        user = users.save(AppUser.register("owner@example.com", "{argon2}x", "Owner", "UTC")).getId();
        other = users.save(AppUser.register("other@example.com", "{argon2}x", "Other", "UTC")).getId();
    }

    @Test
    void listReturnsOnlyThatUsersCategoriesInPositionOrder() {
        repo.save(ProjectCategory.create(user, "Zeta", "#111111", 300));
        repo.save(ProjectCategory.create(user, "Alpha", "#222222", 100));
        repo.save(ProjectCategory.create(user, "Beta", "#333333", 200));
        repo.save(ProjectCategory.create(other, "Not mine", "#444444", 100));

        List<ProjectCategory> categories = repo.findByUserIdOrderByPositionAsc(user);

        assertThat(categories).extracting(ProjectCategory::getName)
                .containsExactly("Alpha", "Beta", "Zeta");
    }

    @Test
    void existsByUserAndNameIsScopedPerUser() {
        repo.save(ProjectCategory.create(user, "Home", "#111111", 100));

        assertThat(repo.existsByUserIdAndName(user, "Home")).isTrue();
        assertThat(repo.existsByUserIdAndName(other, "Home")).isFalse();
        assertThat(repo.existsByUserIdAndName(user, "Work")).isFalse();
    }

    @Test
    void existsByUserAndNameAndIdNotExcludesItselfOnRename() {
        ProjectCategory home = repo.save(ProjectCategory.create(user, "Home", "#111111", 100));
        repo.save(ProjectCategory.create(user, "Work", "#222222", 200));

        assertThat(repo.existsByUserIdAndNameAndIdNot(user, "Home", home.getId())).isFalse();
        assertThat(repo.existsByUserIdAndNameAndIdNot(user, "Work", home.getId())).isTrue();
    }

    @Test
    void findByIdAndUserIdHonoursOwnership() {
        ProjectCategory mine = repo.save(ProjectCategory.create(user, "Home", "#111111", 100));

        assertThat(repo.findByIdAndUserId(mine.getId(), user)).isPresent();
        assertThat(repo.findByIdAndUserId(mine.getId(), other)).isEmpty();
    }
}
