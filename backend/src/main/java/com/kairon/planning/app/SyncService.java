package com.kairon.planning.app;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import com.kairon.common.security.UserId;
import com.kairon.common.sync.ChangeSet;
import com.kairon.journal.api.JournalApi;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.todo.api.TodoApi;
import com.kairon.todo.api.TodoItemView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code planning} module's second application service: {@code GET /sync}'s
 * backend, aggregating changed-since rows across {@code todo}, {@code journal},
 * and {@code projects} (docs/milestones/M11-android-foundation.md D3). Same
 * "pure aggregation, no own domain/repo" shape {@link PlanningService} already
 * set for this module (M6 D1).
 */
@Service
@Transactional(readOnly = true)
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final TodoApi todos;
    private final JournalApi journal;
    private final ProjectsApi projects;
    private final Clock clock;
    private final SyncProperties properties;

    public SyncService(TodoApi todos, JournalApi journal, ProjectsApi projects, Clock clock,
            SyncProperties properties) {
        this.todos = todos;
        this.journal = journal;
        this.projects = projects;
        this.clock = clock;
        this.properties = properties;
    }

    public record Snapshot(
            Instant since,
            ChangeSet<TodoItemView> todos,
            ChangeSet<JournalEntryView> journalEntries,
            ChangeSet<ProjectView> projects,
            ChangeSet<ProjectTaskView> projectTasks) {
    }

    /**
     * {@code capturedAt} is read before any underlying query runs (D4) — a row
     * touched between this call and the queries below is simply included again
     * on the caller's *next* call (harmless — upserts/tombstones are idempotent)
     * rather than silently skipped.
     */
    public Snapshot sync(UserId userId, Instant since, Set<SyncType> types) {
        Instant capturedAt = clock.instant();
        int limit = properties.maxRowsPerType();
        ChangeSet<TodoItemView> todoChanges = types.contains(SyncType.TODO)
                ? todos.changedSince(userId, since, limit) : ChangeSet.empty();
        ChangeSet<JournalEntryView> journalChanges = types.contains(SyncType.JOURNAL)
                ? journal.changedSince(userId, since, limit) : ChangeSet.empty();
        ChangeSet<ProjectView> projectChanges = types.contains(SyncType.PROJECT)
                ? projects.changedSince(userId, since, limit) : ChangeSet.empty();
        ChangeSet<ProjectTaskView> taskChanges = types.contains(SyncType.PROJECT_TASK)
                ? projects.tasksChangedSince(userId, since, limit) : ChangeSet.empty();
        log.debug("Assembled sync snapshot userId={} since={} types={}", userId.value(), since, types);
        return new Snapshot(capturedAt, todoChanges, journalChanges, projectChanges, taskChanges);
    }
}
