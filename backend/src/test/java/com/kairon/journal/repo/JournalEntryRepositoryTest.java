package com.kairon.journal.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.identity.domain.AppUser;
import com.kairon.identity.repo.AppUserRepository;
import com.kairon.journal.domain.JournalEntry;

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
 * Exercises the day/range queries, soft-delete filtering, and the entry-days
 * calendar marker query. Full-text search lives in {@link JournalSearchRepositoryTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class JournalEntryRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 9);

    @Autowired
    JournalEntryRepository repo;

    @Autowired
    AppUserRepository users;

    private UUID user;
    private UUID other;

    @BeforeEach
    void createOwners() {
        user = users.save(AppUser.register("owner@example.com", "{argon2}x", "Owner", "UTC")).getId();
        other = users.save(AppUser.register("other@example.com", "{argon2}x", "Other", "UTC")).getId();
    }

    private JournalEntry entry(UUID owner, LocalDate day, int position, String title) {
        return JournalEntry.create(owner, day, position, title, "some content", null);
    }

    @Test
    void dayQueryReturnsOnlyThatUsersNonDeletedEntriesInPositionOrder() {
        repo.save(entry(user, DAY, 300, "third"));
        repo.save(entry(user, DAY, 100, "first"));
        repo.save(entry(user, DAY, 200, "second"));
        repo.save(entry(other, DAY, 100, "not mine"));
        JournalEntry deleted = entry(user, DAY, 400, "gone");
        deleted.softDelete(Instant.parse("2026-09-09T12:00:00Z"));
        repo.save(deleted);

        List<JournalEntry> entries = repo
                .findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(user, DAY);

        assertThat(entries).extracting(JournalEntry::getTitle)
                .containsExactly("first", "second", "third");
    }

    @Test
    void rangeQueryIsOrderedByDayThenPositionAndExcludesOutsideTheWindow() {
        repo.save(entry(user, DAY.minusDays(1), 100, "yesterday"));
        repo.save(entry(user, DAY, 200, "today-b"));
        repo.save(entry(user, DAY, 100, "today-a"));
        repo.save(entry(user, DAY.plusDays(5), 100, "outside"));

        List<JournalEntry> entries = repo
                .findByUserIdAndDayBetweenAndDeletedAtIsNullOrderByDayAscPositionAsc(
                        user, DAY.minusDays(1), DAY.plusDays(1));

        assertThat(entries).extracting(JournalEntry::getTitle)
                .containsExactly("yesterday", "today-a", "today-b");
    }

    @Test
    void findByIdScopedToUserHonoursOwnershipAndSoftDelete() {
        JournalEntry mine = repo.save(entry(user, DAY, 100, "mine"));

        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), user)).isPresent();
        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), other)).isEmpty();

        mine.softDelete(Instant.parse("2026-09-09T15:00:00Z"));
        repo.save(mine);
        assertThat(repo.findByIdAndUserIdAndDeletedAtIsNull(mine.getId(), user)).isEmpty();
    }

    @Test
    void existsByUserAndDayIgnoresOtherUsersAndSoftDeletedRows() {
        assertThat(repo.existsByUserIdAndDayAndDeletedAtIsNull(user, DAY)).isFalse();

        JournalEntry mine = repo.save(entry(user, DAY, 100, "mine"));
        assertThat(repo.existsByUserIdAndDayAndDeletedAtIsNull(user, DAY)).isTrue();
        assertThat(repo.existsByUserIdAndDayAndDeletedAtIsNull(other, DAY)).isFalse();

        mine.softDelete(Instant.parse("2026-09-09T15:00:00Z"));
        repo.save(mine);
        assertThat(repo.existsByUserIdAndDayAndDeletedAtIsNull(user, DAY)).isFalse();
    }

    @Test
    void entryDaysReturnsDistinctSortedDaysWithAtLeastOneEntry() {
        repo.save(entry(user, DAY, 100, "a"));
        repo.save(entry(user, DAY, 200, "b"));
        repo.save(entry(user, DAY.plusDays(3), 100, "c"));
        repo.save(entry(other, DAY.plusDays(1), 100, "not mine"));
        JournalEntry deleted = entry(user, DAY.plusDays(2), 100, "gone");
        deleted.softDelete(Instant.parse("2026-09-09T15:00:00Z"));
        repo.save(deleted);

        List<LocalDate> days = repo.findEntryDays(user, DAY, DAY.plusDays(5));

        assertThat(days).containsExactly(DAY, DAY.plusDays(3));
    }
}
