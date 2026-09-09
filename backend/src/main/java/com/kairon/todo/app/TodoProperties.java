package com.kairon.todo.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Todo module settings ({@code kairon.todo.*}):
 *
 * <ul>
 *   <li>{@code rollover-look-back-days} — how far back a rollover sweep reaches
 *       for stranded {@code OPEN} items. The list is often skipped for days at a
 *       time, so this is a window, not "yesterday" (docs/milestones/M2 D4).</li>
 *   <li>{@code range-max-days} — the inclusive span cap on
 *       {@code GET /todo?from=&to=}.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "kairon.todo")
public record TodoProperties(
        int rolloverLookBackDays,
        int rangeMaxDays) {

    public TodoProperties {
        if (rolloverLookBackDays <= 0) {
            rolloverLookBackDays = 14;
        }
        if (rangeMaxDays <= 0) {
            rangeMaxDays = 92;
        }
    }
}
