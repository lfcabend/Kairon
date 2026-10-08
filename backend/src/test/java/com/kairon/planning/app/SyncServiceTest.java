package com.kairon.planning.app;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.kairon.common.security.UserId;
import com.kairon.common.sync.ChangeSet;
import com.kairon.journal.api.JournalApi;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.planning.app.SyncService.Snapshot;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.todo.api.TodoApi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));

    @Mock
    TodoApi todos;

    @Mock
    JournalApi journal;

    @Mock
    ProjectsApi projects;

    SyncService service;

    @BeforeEach
    void setUp() {
        service = new SyncService(todos, journal, projects, Clock.fixed(NOW, ZoneOffset.UTC),
                new SyncProperties(0));
    }

    @Test
    void syncOnlyQueriesTheRequestedTypes() {
        Instant since = Instant.EPOCH;
        when(todos.changedSince(USER, since, 500)).thenReturn(ChangeSet.empty());

        Snapshot snapshot = service.sync(USER, since, Set.of(SyncType.TODO));

        assertThat(snapshot.todos()).isEqualTo(ChangeSet.empty());
        assertThat(snapshot.journalEntries()).isEqualTo(ChangeSet.<JournalEntryView>empty());
        assertThat(snapshot.projects()).isEqualTo(ChangeSet.<ProjectView>empty());
        assertThat(snapshot.projectTasks()).isEqualTo(ChangeSet.<ProjectTaskView>empty());
        verify(journal, never()).changedSince(any(), any(), anyInt());
        verify(projects, never()).changedSince(any(), any(), anyInt());
        verify(projects, never()).tasksChangedSince(any(), any(), anyInt());
    }

    @Test
    void sinceIsCapturedBeforeDelegatingToAnyApi() {
        Snapshot snapshot = service.sync(USER, Instant.EPOCH, EnumSet.allOf(SyncType.class));

        assertThat(snapshot.since()).isEqualTo(NOW);
    }

    @Test
    void anEmptyAccountReturnsAllEmptyChangeSetsNotAnError() {
        Instant since = Instant.EPOCH;
        when(todos.changedSince(USER, since, 500)).thenReturn(ChangeSet.empty());
        when(journal.changedSince(USER, since, 500)).thenReturn(ChangeSet.empty());
        when(projects.changedSince(USER, since, 500)).thenReturn(ChangeSet.empty());
        when(projects.tasksChangedSince(USER, since, 500)).thenReturn(ChangeSet.empty());

        Snapshot snapshot = service.sync(USER, since, EnumSet.allOf(SyncType.class));

        assertThat(snapshot.todos().upserted()).isEmpty();
        assertThat(snapshot.journalEntries().upserted()).isEmpty();
        assertThat(snapshot.projects().upserted()).isEmpty();
        assertThat(snapshot.projectTasks().upserted()).isEmpty();
    }
}
