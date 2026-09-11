package com.kairon.journal.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The public projection of a journal entry for other modules and the web
 * layer's response DTO. Feature modules depend on this record and the
 * {@link JournalApi} port, never on {@code journal.domain} or {@code journal.repo}
 * (ArchUnit-enforced — docs/DESIGN.md §3.1).
 */
public record JournalEntryView(
        UUID id,
        LocalDate day,
        int position,
        String title,
        String content,
        Integer mood,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
