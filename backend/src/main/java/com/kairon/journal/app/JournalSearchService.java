package com.kairon.journal.app;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.journal.api.JournalSearchHitView;
import com.kairon.journal.api.JournalSearchPage;
import com.kairon.journal.repo.JournalEntryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thin wrapper over {@link JournalEntryRepository#search}, mapping
 * {@code JournalSearchRow} projections to the API's {@link JournalSearchHitView}.
 * Kept separate from {@link JournalService} because it's a different shape of
 * query (paginated, ranked, projection-based) rather than because the domain
 * differs (docs/milestones/M3 §4.4).
 */
@Service
@Transactional(readOnly = true)
public class JournalSearchService {

    private static final Logger log = LoggerFactory.getLogger(JournalSearchService.class);

    private final JournalEntryRepository entries;

    public JournalSearchService(JournalEntryRepository entries) {
        this.entries = entries;
    }

    public JournalSearchPage search(UserId userId, String q, int page, int size) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            throw ApiException.badRequest("`q` must not be blank.");
        }
        Page<JournalSearchHitView> result = entries.search(userId.value(), query, PageRequest.of(page, size))
                .map(JournalMapper::toHitView);
        log.debug("Searched journal userId={} q=\"{}\" -> {} hit(s) (page {}/{})",
                userId.value(), query, result.getNumberOfElements(), result.getNumber(), result.getTotalPages());
        return new JournalSearchPage(result.getContent(), result.getNumber(), result.getTotalElements());
    }
}
