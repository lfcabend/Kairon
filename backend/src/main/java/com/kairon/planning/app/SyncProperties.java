package com.kairon.planning.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sync module settings ({@code kairon.sync.*}):
 *
 * <ul>
 *   <li>{@code max-rows-per-type} — the per-type cap on one {@code GET /sync}
 *       call (M11 D5). More than this many changed rows since the caller's
 *       cursor sets that type's {@code truncated} flag rather than failing
 *       the call or paginating.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "kairon.sync")
public record SyncProperties(int maxRowsPerType) {

    public SyncProperties {
        if (maxRowsPerType <= 0) {
            maxRowsPerType = 500;
        }
    }
}
