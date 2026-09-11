package com.kairon.journal.repo;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-text search (ROADMAP's explicit ask): {@code websearch_to_tsquery}
 * phrase/{@code -exclude}/{@code OR} semantics, {@code ts_rank} ordering,
 * {@code ts_headline} snippets, pagination, per-user scoping, soft-delete
 * exclusion (docs/milestones/M3 §7).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class JournalSearchRepositoryTest {

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

    private void save(UUID owner, LocalDate day, int position, String title, String content) {
        repo.save(JournalEntry.create(owner, day, position, title, content, null));
    }

    @Test
    void matchesOnContentOrTitleAndScopesToTheQueryingUser() {
        save(user, DAY, 100, "Morning plan", "Ship the **M3 plan** today.");
        save(user, DAY, 200, "Unrelated", "Nothing to see here.");
        save(other, DAY, 100, "Also planning", "plan plan plan but not mine");

        Page<JournalSearchRow> page = repo.search(user, "plan", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(JournalSearchRow::getTitle).containsExactly("Morning plan");
    }

    @Test
    void excludesSoftDeletedEntries() {
        JournalEntry deleted = JournalEntry.create(user, DAY, 100, "Gone", "findme content", null);
        deleted.softDelete(java.time.Instant.parse("2026-09-09T12:00:00Z"));
        repo.save(deleted);

        Page<JournalSearchRow> page = repo.search(user, "findme", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void ranksBetterMatchesFirst() {
        save(user, DAY, 100, null, "ship mentioned once");
        save(user, DAY, 200, "ship ship ship", "ship ship ship ship");

        Page<JournalSearchRow> page = repo.search(user, "ship", PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(JournalSearchRow::getTitle)
                .containsExactly("ship ship ship", null);
    }

    @Test
    void snippetContainsTheMatchedTermHighlighted() {
        save(user, DAY, 100, "Evening reflection", "Today I decided to ship the plan before dinner.");

        Page<JournalSearchRow> page = repo.search(user, "ship", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getSnippet()).containsIgnoringCase("<b>ship</b>");
    }

    @Test
    void supportsQuotedPhraseExcludeAndOrSyntax() {
        save(user, DAY, 100, "A", "the quick brown fox");
        save(user, DAY, 200, "B", "quick unrelated fox jumped");
        save(user, DAY, 300, "C", "a slow brown bear");

        assertThat(repo.search(user, "\"quick brown\"", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(repo.search(user, "brown -bear", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(repo.search(user, "quick OR bear", PageRequest.of(0, 10)).getTotalElements()).isEqualTo(3);
    }

    @Test
    void paginatesResults() {
        for (int i = 0; i < 5; i++) {
            save(user, DAY, 100 + i, "Entry " + i, "keyword content " + i);
        }

        Page<JournalSearchRow> firstPage = repo.search(user, "keyword", PageRequest.of(0, 2));
        Page<JournalSearchRow> secondPage = repo.search(user, "keyword", PageRequest.of(1, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(2);
    }

    @Test
    void moodAndOtherProjectedFieldsRoundTrip() {
        JournalEntry entry = JournalEntry.create(user, DAY, 100, "Reflective", "keyword content here", 4);
        repo.save(entry);

        List<JournalSearchRow> hits = repo.search(user, "keyword", PageRequest.of(0, 10)).getContent();

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getMood()).isEqualTo((short) 4);
        assertThat(hits.get(0).getId()).isEqualTo(entry.getId());
        assertThat(hits.get(0).getDay()).isEqualTo(DAY);
        assertThat(hits.get(0).getVersion()).isEqualTo(entry.getVersion());
    }
}
