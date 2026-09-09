package com.kairon.todo.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.security.UserId;
import com.kairon.todo.api.TodoItemView;
import com.kairon.todo.app.RolloverService.RolloverCommand;
import com.kairon.todo.app.RolloverService.RolloverPreview;
import com.kairon.todo.app.RolloverService.UndoCommand;
import com.kairon.todo.domain.TodoItem;
import com.kairon.todo.domain.TodoStatus;
import com.kairon.todo.repo.TodoItemRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolloverServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000c1"));
    private static final LocalDate TO_DAY = LocalDate.of(2026, 9, 9);

    @Mock
    TodoItemRepository items;

    RolloverService service;

    @BeforeEach
    void setUp() {
        service = new RolloverService(items, new TodoProperties(0, 0), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TodoItem open(LocalDate day, int position, String title) {
        return TodoItem.create(USER.value(), day, title, "notes-" + title, 2, position, 45,
                UUID.fromString("018f5b3e-0000-7000-8000-0000000000ff"));
    }

    private void stubSweep(List<TodoItem> result) {
        when(items.findByUserIdAndStatusAndDayLessThanAndDayGreaterThanEqualAndDeletedAtIsNullOrderByDayAscPositionAsc(
                eq(USER.value()), eq(TodoStatus.OPEN), eq(TO_DAY), eq(TO_DAY.minusDays(14))))
                .thenReturn(result);
    }

    @Test
    void previewGroupsEligibleOpenItemsBySourceDayOldestFirst() {
        stubSweep(List.of(
                open(TO_DAY.minusDays(6), 100, "old-a"),
                open(TO_DAY.minusDays(6), 200, "old-b"),
                open(TO_DAY.minusDays(2), 100, "recent")));

        RolloverPreview preview = service.preview(USER, TO_DAY);

        assertThat(preview.totalItems()).isEqualTo(3);
        assertThat(preview.sourceDays()).extracting(RolloverService.SourceDay::day)
                .containsExactly(TO_DAY.minusDays(6), TO_DAY.minusDays(2));
        assertThat(preview.sourceDays().get(0).items()).extracting(TodoItemView::title)
                .containsExactly("old-a", "old-b");
    }

    @Test
    void rolloverWithoutIdsSweepsEveryEligibleDayCancelsSourcesAndCarriesContent() {
        TodoItem s1 = open(TO_DAY.minusDays(5), 100, "carry-1");
        TodoItem s2 = open(TO_DAY.minusDays(2), 100, "carry-2");
        stubSweep(List.of(s1, s2));
        when(items.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), TO_DAY))
                .thenReturn(List.of());
        when(items.save(any(TodoItem.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TodoItemView> created = service.rollover(USER, new RolloverCommand(TO_DAY, null, null));

        assertThat(created).extracting(TodoItemView::title).containsExactly("carry-1", "carry-2");
        assertThat(created).extracting(TodoItemView::position).containsExactly(100, 200);
        assertThat(created).allSatisfy(v -> {
            assertThat(v.day()).isEqualTo(TO_DAY);
            assertThat(v.status()).isEqualTo("OPEN");
            assertThat(v.notes()).startsWith("notes-");
            assertThat(v.priority()).isEqualTo(2);
            assertThat(v.estimateMinutes()).isEqualTo(45);
            assertThat(v.sourceProjectTaskId()).isNotNull();
            assertThat(v.rolledOverFromId()).isNotNull();
        });
        assertThat(s1.getStatus()).isEqualTo(TodoStatus.CANCELLED);
        assertThat(s2.getStatus()).isEqualTo(TodoStatus.CANCELLED);
    }

    @Test
    void rolloverWithExplicitIdsCarriesOnlyThatSubset() {
        TodoItem chosen = open(TO_DAY.minusDays(3), 100, "chosen");
        when(items.findByIdAndUserIdAndDeletedAtIsNull(chosen.getId(), USER.value()))
                .thenReturn(Optional.of(chosen));
        when(items.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), TO_DAY))
                .thenReturn(List.of());
        when(items.save(any(TodoItem.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TodoItemView> created = service.rollover(USER,
                new RolloverCommand(TO_DAY, null, List.of(chosen.getId())));

        assertThat(created).extracting(TodoItemView::title).containsExactly("chosen");
        assertThat(chosen.getStatus()).isEqualTo(TodoStatus.CANCELLED);
    }

    @Test
    void undoReopensSourcesAndSoftDeletesTheCreatedItems() {
        TodoItem source = open(TO_DAY.minusDays(2), 100, "src");
        source.cancel();
        TodoItem created = TodoItem.rolledFrom(source, TO_DAY, 100);
        when(items.findByIdAndUserIdAndDeletedAtIsNull(created.getId(), USER.value()))
                .thenReturn(Optional.of(created));
        when(items.findByIdAndUserIdAndDeletedAtIsNull(source.getId(), USER.value()))
                .thenReturn(Optional.of(source));

        List<TodoItemView> reopened = service.undo(USER, new UndoCommand(List.of(created.getId())));

        assertThat(reopened).extracting(TodoItemView::title).containsExactly("src");
        assertThat(source.getStatus()).isEqualTo(TodoStatus.OPEN);
        assertThat(created.isDeleted()).isTrue();
    }
}
