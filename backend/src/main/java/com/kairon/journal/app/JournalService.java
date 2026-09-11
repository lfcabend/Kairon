package com.kairon.journal.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.journal.api.JournalApi;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.journal.domain.JournalEntry;
import com.kairon.journal.repo.JournalEntryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The journal module's application service: day/range reads, CRUD, and the
 * calendar's entry-days marker query. Every method takes a {@link UserId} and
 * scopes its queries by it; a missing or foreign row is a 404, never a 403
 * (docs/DESIGN.md §3.2).
 *
 * <p>Entries within a day are append-only (D4): {@code position} is assigned
 * on create as {@code max + 100} (same sparse scheme as {@code todo_item}) and
 * there is no reorder endpoint in M3.
 */
@Service
public class JournalService implements JournalApi {

    private static final Logger log = LoggerFactory.getLogger(JournalService.class);
    private static final int POSITION_GAP = 100;

    private final JournalEntryRepository entries;
    private final JournalProperties properties;
    private final Clock clock;

    public JournalService(JournalEntryRepository entries, JournalProperties properties, Clock clock) {
        this.entries = entries;
        this.properties = properties;
        this.clock = clock;
    }

    public record CreateCommand(LocalDate day, String title, String content, Integer mood) {
    }

    /** Null fields are left unchanged; {@code expectedVersion} is optional optimistic-lock check. */
    public record PatchCommand(String title, String content, Integer mood, Long expectedVersion) {
    }

    @Transactional(readOnly = true)
    public List<JournalEntryView> list(UserId userId, LocalDate day) {
        List<JournalEntryView> views = entries
                .findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(userId.value(), day)
                .stream()
                .map(JournalMapper::toView)
                .toList();
        log.debug("Listed {} journal entr(ies) userId={} day={}", views.size(), userId.value(), day);
        return views;
    }

    @Transactional(readOnly = true)
    public List<JournalEntryView> range(UserId userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("`to` must not be before `from`.");
        }
        if (ChronoUnit.DAYS.between(from, to) > properties.rangeMaxDays()) {
            throw ApiException.badRequest(
                    "Date range must not exceed " + properties.rangeMaxDays() + " days.");
        }
        List<JournalEntryView> views = entries
                .findByUserIdAndDayBetweenAndDeletedAtIsNullOrderByDayAscPositionAsc(
                        userId.value(), from, to)
                .stream()
                .map(JournalMapper::toView)
                .toList();
        log.debug("Listed {} journal entr(ies) userId={} range {}..{}", views.size(), userId.value(), from, to);
        return views;
    }

    @Transactional(readOnly = true)
    public List<LocalDate> entryDays(UserId userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("`to` must not be before `from`.");
        }
        if (ChronoUnit.DAYS.between(from, to) > properties.rangeMaxDays()) {
            throw ApiException.badRequest(
                    "Date range must not exceed " + properties.rangeMaxDays() + " days.");
        }
        return entries.findEntryDays(userId.value(), from, to);
    }

    @Transactional
    public JournalEntryView create(UserId userId, CreateCommand command) {
        int position = nextPosition(userId.value(), command.day());
        JournalEntry entry;
        try {
            entry = JournalEntry.create(
                    userId.value(), command.day(), position,
                    trimToNull(command.title()), command.content(), command.mood());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        JournalEntry saved = entries.save(entry);
        log.info("Created journal entry {} userId={} day={}", saved.getId(), userId.value(), command.day());
        return JournalMapper.toView(saved);
    }

    @Transactional
    public JournalEntryView patch(UserId userId, UUID id, PatchCommand command) {
        JournalEntry entry = require(userId, id);
        if (command.expectedVersion() != null && command.expectedVersion() != entry.getVersion()) {
            log.warn("Patch rejected: version conflict on journal entry {} userId={} (expected {}, actual {})",
                    id, userId.value(), command.expectedVersion(), entry.getVersion());
            throw ApiException.conflict(
                    "This entry was modified by another request. Reload and try again.");
        }
        String title = command.title() != null ? trimToNull(command.title()) : entry.getTitle();
        String content = command.content() != null ? command.content() : entry.getContent();
        Integer mood = command.mood() != null ? command.mood() : entry.getMood();
        try {
            entry.edit(title, content, mood);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        log.info("Patched journal entry {} userId={}", id, userId.value());
        return JournalMapper.toView(entry);
    }

    @Transactional
    public void softDelete(UserId userId, UUID id) {
        require(userId, id).softDelete(clock.instant());
        log.info("Soft-deleted journal entry {} userId={}", id, userId.value());
    }

    // --- JournalApi port -------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean hasEntryForDay(UserId userId, LocalDate day) {
        return entries.existsByUserIdAndDayAndDeletedAtIsNull(userId.value(), day);
    }

    // --- internals -----------------------------------------------------------

    private JournalEntry require(UserId userId, UUID id) {
        return entries.findByIdAndUserIdAndDeletedAtIsNull(id, userId.value())
                .orElseThrow(() -> {
                    log.debug("Journal entry {} not visible to userId={} (missing, deleted, or foreign)",
                            id, userId.value());
                    return ApiException.notFound("Journal entry not found.");
                });
    }

    private int nextPosition(UUID userId, LocalDate day) {
        return entries.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(userId, day)
                .stream()
                .mapToInt(JournalEntry::getPosition)
                .max()
                .orElse(0) + POSITION_GAP;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
