package com.kairon.common.sync;

import java.util.List;
import java.util.UUID;

/**
 * One module's slice of a {@code GET /sync} response: rows changed since the
 * caller's cursor, split into full upserts and bare tombstone ids (soft
 * deletes don't need — and shouldn't echo — the deleted row's own fields).
 * Generic shared-kernel infrastructure, not feature logic (same precedent as
 * {@link com.kairon.common.id.Uuidv7}) — see
 * docs/milestones/M11-android-foundation.md D3.
 */
public record ChangeSet<T>(List<T> upserted, List<UUID> deletedIds, boolean truncated) {

    public static <T> ChangeSet<T> empty() {
        return new ChangeSet<>(List.of(), List.of(), false);
    }
}
