package com.kairon.journal.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;
import com.kairon.journal.app.JournalSearchService;
import com.kairon.journal.app.JournalService;
import com.kairon.journal.app.JournalService.CreateCommand;
import com.kairon.journal.app.JournalService.PatchCommand;
import com.kairon.journal.web.JournalDtos.CreateJournalEntryRequest;
import com.kairon.journal.web.JournalDtos.JournalEntryResponse;
import com.kairon.journal.web.JournalDtos.JournalSearchPageResponse;
import com.kairon.journal.web.JournalDtos.PatchJournalEntryRequest;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/journal} — the journal day view and search screen's backend.
 * Mirrors {@code TodoController}: constructor injection, {@code @CurrentUser
 * UserId}, {@code @Valid} bodies, and DTOs only. Day/range list endpoints
 * return a bare array (same reasoning as Todo — docs/milestones/M2 §5);
 * {@code :search} returns the standard paginated envelope (D3).
 *
 * <p>The class mapping is {@code /api/v1} rather than {@code /api/v1/journal}
 * so the AIP-style custom method ({@code /journal:search}) resolves — Spring's
 * path combiner would otherwise insert a slash before the colon.
 */
@RestController
@RequestMapping("/api/v1")
public class JournalController {

    private static final Logger log = LoggerFactory.getLogger(JournalController.class);

    private final JournalService journal;
    private final JournalSearchService search;

    public JournalController(JournalService journal, JournalSearchService search) {
        this.journal = journal;
        this.search = search;
    }

    @GetMapping("/journal")
    public List<JournalEntryResponse> list(
            @CurrentUser UserId userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate day,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to) {
        log.debug("GET /journal userId={} day={} from={} to={}", userId.value(), day, from, to);
        if (day != null) {
            return JournalEntryResponse.from(journal.list(userId, day));
        }
        if (from != null && to != null) {
            return JournalEntryResponse.from(journal.range(userId, from, to));
        }
        throw ApiException.badRequest("Provide either `day` or both `from` and `to`.");
    }

    @GetMapping("/journal/entry-days")
    public List<LocalDate> entryDays(
            @CurrentUser UserId userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("GET /journal/entry-days userId={} from={} to={}", userId.value(), from, to);
        return journal.entryDays(userId, from, to);
    }

    @PostMapping("/journal")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalEntryResponse create(@CurrentUser UserId userId,
            @Valid @RequestBody CreateJournalEntryRequest request) {
        log.debug("POST /journal userId={} day={}", userId.value(), request.day());
        return JournalEntryResponse.from(journal.create(userId,
                new CreateCommand(request.day(), request.title(), request.content(), request.mood())));
    }

    @PatchMapping("/journal/{id}")
    public JournalEntryResponse patch(@CurrentUser UserId userId, @PathVariable UUID id,
            @Valid @RequestBody PatchJournalEntryRequest request) {
        log.debug("PATCH /journal/{} userId={}", id, userId.value());
        return JournalEntryResponse.from(journal.patch(userId, id,
                new PatchCommand(request.title(), request.content(), request.mood(), request.expectedVersion())));
    }

    @DeleteMapping("/journal/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("DELETE /journal/{} userId={}", id, userId.value());
        journal.softDelete(userId, id);
    }

    @GetMapping("/journal:search")
    public JournalSearchPageResponse search(
            @CurrentUser UserId userId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("GET /journal:search userId={} q=\"{}\" page={} size={}", userId.value(), q, page, size);
        return JournalSearchPageResponse.from(search.search(userId, q, page, size));
    }
}
