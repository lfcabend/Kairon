package com.kairon.android.core.sync

/**
 * A fixed set of `/sync` types a caller may request, each paired with its
 * own cursor in [SyncCursorStore]. [SyncRepository]'s own doc comment (and
 * [SyncCursorStore]'s) explain why a single shared cursor only makes sense
 * for a *fixed* type set: advancing one cursor past a partial request would
 * silently tell the rest of the app "we have the complete state of every
 * type up to this point" when that's not true for the types left out.
 *
 * [FULL] is every screen-driven sync (`TodoViewModel`, `JournalViewModel`,
 * `ProjectDetailViewModel`, ...) and keeps the original cursor keys so an
 * app update doesn't lose an existing device's progress. [NOTIFICATIONS] is
 * `NotificationCheckWorker`'s own narrower periodic sync (M11.9) — it only
 * ever needs `todo`/`projectTask`, and pulling `journal`/`project` on every
 * ~30-minute tick would be pure waste (extra queries/payload, and background
 * fetching journal content for a feature that never reads it).
 */
enum class SyncScope(val requestedTypes: List<String>) {
    FULL(listOf("todo", "journal", "project", "projectTask")),
    NOTIFICATIONS(listOf("todo", "projectTask")),
}
