package com.kairon.journal.app;

import com.kairon.journal.api.JournalEntryView;
import com.kairon.journal.api.JournalSearchHitView;
import com.kairon.journal.domain.JournalEntry;
import com.kairon.journal.repo.JournalSearchRow;

/**
 * Hand-rolled entity/projection → view mapping, following the {@code todo}
 * precedent (static factory, no MapStruct — docs/milestones/M2 D5).
 */
final class JournalMapper {

    private JournalMapper() {
    }

    static JournalEntryView toView(JournalEntry entry) {
        return new JournalEntryView(
                entry.getId(),
                entry.getDay(),
                entry.getPosition(),
                entry.getTitle(),
                entry.getContent(),
                entry.getMood(),
                entry.getCreatedAt(),
                entry.getUpdatedAt(),
                entry.getVersion());
    }

    static JournalSearchHitView toHitView(JournalSearchRow row) {
        return new JournalSearchHitView(
                row.getId(),
                row.getDay(),
                row.getTitle(),
                row.getSnippet(),
                row.getMood() == null ? null : row.getMood().intValue(),
                row.getCreatedAt());
    }
}
