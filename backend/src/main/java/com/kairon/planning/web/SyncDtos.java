package com.kairon.planning.web;

import java.time.Instant;

import com.kairon.common.sync.ChangeSet;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.planning.app.SyncService;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;
import com.kairon.todo.api.TodoItemView;

/**
 * Response body for {@code /api/v1/sync}. Embeds the other modules' own
 * {@code api} DTOs directly inside {@link ChangeSet} (same
 * "no extra translation" convention {@code PlanningDtos} already set, M6
 * D12) — {@code planning} has no domain of its own to translate them into.
 */
final class SyncDtos {

    private SyncDtos() {
    }

    record SyncResponse(
            Instant since,
            ChangeSet<TodoItemView> todos,
            ChangeSet<JournalEntryView> journalEntries,
            ChangeSet<ProjectView> projects,
            ChangeSet<ProjectTaskView> projectTasks) {

        static SyncResponse from(SyncService.Snapshot snapshot) {
            return new SyncResponse(snapshot.since(), snapshot.todos(), snapshot.journalEntries(),
                    snapshot.projects(), snapshot.projectTasks());
        }
    }
}
