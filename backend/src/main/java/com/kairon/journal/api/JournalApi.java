package com.kairon.journal.api;

import java.time.LocalDate;

import com.kairon.common.security.UserId;

/**
 * The journal module's public port. Other modules depend only on this
 * interface and the DTOs in this package, never on {@code journal.domain} or
 * {@code journal.repo} (ArchUnit-enforced — docs/DESIGN.md §3.1). Implemented
 * by {@code com.kairon.journal.app.JournalService}.
 *
 * <p>Kept deliberately minimal (D6): M6 {@code planning}'s "Today" screen only
 * needs to know whether to show the journal prompt, not the entry content.
 */
public interface JournalApi {

    /** Whether the user has at least one non-deleted entry on {@code day}. */
    boolean hasEntryForDay(UserId userId, LocalDate day);
}
