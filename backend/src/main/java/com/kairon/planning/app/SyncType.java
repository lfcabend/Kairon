package com.kairon.planning.app;

import java.util.EnumSet;
import java.util.Set;

import com.kairon.common.error.ApiException;

/**
 * Which of {@code GET /sync}'s four change-sets a caller wants (M11
 * docs/milestones/M11-android-foundation.md D3). The {@code types} query
 * param defaults to "all four" when omitted so a new client always gets a
 * usable snapshot without having to know the full list up front; a client
 * that only consumes a subset (M11's own Android app passes just
 * {@code todo}) narrows it to skip the other queries entirely.
 */
public enum SyncType {
    TODO("todo"),
    JOURNAL("journal"),
    PROJECT("project"),
    PROJECT_TASK("projectTask");

    private final String wireName;

    SyncType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    /** {@code null} or empty -> every type. An unknown name is a 400, not a silent skip. */
    public static Set<SyncType> parse(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EnumSet.allOf(SyncType.class);
        }
        Set<SyncType> result = EnumSet.noneOf(SyncType.class);
        for (String name : raw) {
            result.add(fromWireName(name));
        }
        return result;
    }

    private static SyncType fromWireName(String name) {
        for (SyncType type : values()) {
            if (type.wireName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw ApiException.badRequest("Unknown sync type: " + name);
    }
}
