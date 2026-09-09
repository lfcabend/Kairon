package com.kairon.todo.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.identity.domain.AppUser;
import com.kairon.identity.repo.AppUserRepository;
import com.kairon.todo.domain.TodoItem;
import com.kairon.todo.domain.TodoStatus;

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

/**
 * Repository slice against a real PostgreSQL (docs/testing approach: never H2).
 * Exercises the day/range queries, soft-delete filtering, and the rollover sweep.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class TodoItemRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 9);

    @Autowired
    TodoItemRepository repo;

    @Autowired
    AppUserRepository users;

    private UUID user;
    private UUID other;

    @BeforeEach
    void createOwners() {
        user = users.save(AppUser.register("owner@example.com", "{argon2}x", "Owner", "UTC")).getId();
        other = users.save(AppUser.register("other@example.com", "{argon2}x", "Other", "UTC")).getId();
    }

    private TodoItem item(UUID owner, LocalDate day, int position, String title) {
        return TodoItem.create(owner, day, title, null, 0, position, null, null);
    }

    @Test
    void dayQueryReturnsOnlyThatUsersNonDeletedItemsInPositionOrder() {
        repo.save(item(user, DAY, 300, "third"));
        repo.save(item(user, DAY, 100, "first"));
        repo.save(item(user, DAY, 200, "second"));
        repo.save(item(other, DAY, 100, "not mine"));
        TodoItem deleted = item(user, DAY, 400, "gone");
        deleted.softDelete(Instant.parse("2026-09-09T12:00:00Z"));
        repo.save(deleted);

        List<TodoItem> items = repo
                .findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(user, DAY);

        assertThat(items).extracting(TodoItem::getTitle)
                .containsExactly("first", "second", "third");
    }

    @Test
    void rangeQueryIsOrderedByDayThenPositionAndExcludesEndpointsOutsideTheWindow() {
        repo.save(item(user, DAY.minusDays(1), 100, "yesterday"));
        repo.save(item(user, DAY, 200, "today-b"));
        repo.save(item(user, DAY, 100, "today-a"));
        repo.save(item(user, DAY.plusDays(5), 100, "outside"));

        List<TodoItem> items = repo
                .findByUserIdAndDayBetweenAndDeletedAtIsNullOrderByDayAscPositionAsc(
                        user, DAY.minusDays(1), DAY.plusDays(1));

        assertThat(items).extracting(TodoItem::getTitle)
                .containsExactly("yesterday", "today-a", "today-b");
    }

    @Test
    void rolloverSweepReturnsOpenItemsAcrossSkippedDaysOldestFirstAndRespectsTheBound() {
        repo.save(item(user, DAY.minusDays(9), 100, "too old"));
        repo.save(item(user, DAY.minusDays(6), 100, "old open"));
        repo.save(item(user, DAY.minusDays(2), 100, "recent open"));

        TodoItem done = item(user, DAY.minusDays(3), 100, "done");
        done.complete(Instant.parse("2026-09-06T09:00:00Z"));
        repo.save(done);

        TodoItem cancelled = item(user, DAY.minusDays(3), 200, "cancelled");
        cancelled.cancel();
        repo.save(cancelled);

        TodoItem deleted = item(user, DAY.minusDays(2), 200, "deleted");
        deleted.softDelete(Instant.parse("2026-09-07T09:00:00Z"));
        repo.save(deleted);

        repo.save(item(other, DAY.minusDays(2), 100, "someone else"));

        List<TodoItem> swept = repo
                .findByUserIdAndStatusAndDayLessThanAndDayGreaterThanEqualAndDeletedAtIsNullOrderByDayAscPositionAsc(
                        user, TodoStatus.OPEN, DAY, DAY.minusDays(7));

        assertThat(swept).extracting(TodoItem::getTitle)
                .containsExactly("old open", "recent open");
    }

    @Test
    void findByIdScopedToUserHonoursOwnershipAndSoftDelete() {
        TodoItem mine = repo.save(item(user, DAY, 100, "mine"));

        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), user)).isPresent();
        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), other)).isEmpty();

        mine.softDelete(Instant.parse("2026-09-09T15:00:00Z"));
        repo.save(mine);
        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), user)).isEmpty();
    }
}
