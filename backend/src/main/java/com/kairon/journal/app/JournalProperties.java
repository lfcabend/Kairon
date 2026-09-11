package com.kairon.journal.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Journal module settings ({@code kairon.journal.*}):
 *
 * <ul>
 *   <li>{@code range-max-days} — the inclusive span cap on
 *       {@code GET /journal?from=&to=}. Mirrors {@code TodoProperties} (no
 *       look-back-window equivalent — journal has no rollover concept).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "kairon.journal")
public record JournalProperties(int rangeMaxDays) {

    public JournalProperties {
        if (rangeMaxDays <= 0) {
            rangeMaxDays = 92;
        }
    }
}
