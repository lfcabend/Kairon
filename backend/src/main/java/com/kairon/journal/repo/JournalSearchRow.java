package com.kairon.journal.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Spring Data interface projection for {@link JournalEntryRepository#search}.
 * Deliberately excludes {@code content} — the {@code snippet} is enough for a
 * result row (docs/milestones/M3 §4.3).
 */
public interface JournalSearchRow {

    UUID getId();

    LocalDate getDay();

    int getPosition();

    String getTitle();

    Short getMood();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    long getVersion();

    String getSnippet();

    double getRank();
}
