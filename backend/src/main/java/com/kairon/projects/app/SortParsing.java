package com.kairon.projects.app;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;

/**
 * Parses the standard {@code ?sort=property,direction} query convention into a
 * {@link Sort}. Kept in {@code app} rather than {@code web} — {@code Sort}
 * lives in {@code org.springframework.data.domain}, which matches the
 * ArchUnit {@code ..domain..} glob just as much as a JPA entity's package does
 * (docs/milestones/M3's {@code JournalSearchPage} precedent), so the web layer
 * never imports it directly.
 */
final class SortParsing {

    private SortParsing() {
    }

    static Sort parse(List<String> sortParams) {
        if (sortParams == null || sortParams.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String param : sortParams) {
            String[] parts = param.split(",");
            String property = parts[0];
            Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            orders.add(new Sort.Order(direction, property));
        }
        return Sort.by(orders);
    }
}
