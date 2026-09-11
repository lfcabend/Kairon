package com.kairon.journal.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One ranked full-text search result: the entry's identity, its
 * {@code ts_headline} snippet, and enough to render a result row without
 * loading the full {@code content} (D7).
 */
public record JournalSearchHitView(
        UUID id,
        LocalDate day,
        String title,
        String snippet,
        Integer mood,
        Instant createdAt) {
}
