package com.kairon.journal.api;

import java.util.List;

/**
 * A paginated slice of ranked search hits — the {@code api} package's own
 * paging shape so the web layer never has to import
 * {@code org.springframework.data.domain.Page} (whose package name happens to
 * match the ArchUnit {@code ..domain..} glob just as much as a JPA entity's
 * does).
 */
public record JournalSearchPage(List<JournalSearchHitView> content, int page, long totalElements) {
}
