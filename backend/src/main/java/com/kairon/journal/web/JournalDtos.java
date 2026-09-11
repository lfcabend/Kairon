package com.kairon.journal.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.journal.api.JournalEntryView;
import com.kairon.journal.api.JournalSearchHitView;
import com.kairon.journal.api.JournalSearchPage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request/response bodies for {@code /api/v1/journal}. Controllers never see
 * the entity (ArchUnit-enforced); the {@code from} factories are the one
 * mapping point from the {@code api} view to the wire shape.
 */
final class JournalDtos {

    private JournalDtos() {
    }

    record CreateJournalEntryRequest(
            @NotNull LocalDate day,
            @Size(max = 200) String title,
            String content,
            @Min(1) @Max(5) Integer mood) {
    }

    /** All fields optional; a null field leaves that attribute unchanged. */
    record PatchJournalEntryRequest(
            @Size(max = 200) String title,
            String content,
            @Min(1) @Max(5) Integer mood,
            Long expectedVersion) {
    }

    record JournalEntryResponse(
            UUID id,
            LocalDate day,
            int position,
            String title,
            String content,
            Integer mood,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static JournalEntryResponse from(JournalEntryView v) {
            return new JournalEntryResponse(v.id(), v.day(), v.position(), v.title(), v.content(),
                    v.mood(), v.createdAt(), v.updatedAt(), v.version());
        }

        static List<JournalEntryResponse> from(List<JournalEntryView> views) {
            return views.stream().map(JournalEntryResponse::from).toList();
        }
    }

    record JournalSearchHitResponse(
            UUID id,
            LocalDate day,
            String title,
            String snippet,
            Integer mood,
            Instant createdAt) {

        static JournalSearchHitResponse from(JournalSearchHitView v) {
            return new JournalSearchHitResponse(v.id(), v.day(), v.title(), v.snippet(), v.mood(), v.createdAt());
        }
    }

    record JournalSearchPageResponse(List<JournalSearchHitResponse> content, int page, long totalElements) {

        static JournalSearchPageResponse from(JournalSearchPage p) {
            return new JournalSearchPageResponse(
                    p.content().stream().map(JournalSearchHitResponse::from).toList(),
                    p.page(),
                    p.totalElements());
        }
    }
}
