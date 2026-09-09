package com.kairon.todo.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.todo.api.TodoItemView;
import com.kairon.todo.app.TodoService.CreateCommand;
import com.kairon.todo.app.TodoService.PatchCommand;
import com.kairon.todo.domain.TodoItem;
import com.kairon.todo.repo.TodoItemRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000b1"));
    private static final LocalDate DAY = LocalDate.of(2026, 9, 9);

    @Mock
    TodoItemRepository items;

    TodoService service;

    @BeforeEach
    void setUp() {
        service = new TodoService(items, new TodoProperties(0, 0), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TodoItem itemOnDay(int position) {
        return TodoItem.create(USER.value(), DAY, "task@" + position, null, 0, position, null, null);
    }

    @Test
    void createAppendsAtMaxPositionPlus100() {
        when(items.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), DAY))
                .thenReturn(List.of(itemOnDay(100), itemOnDay(250)));
        when(items.save(any(TodoItem.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoItemView created = service.create(USER,
                new CreateCommand(DAY, "  new thing  ", null, null, null, null));

        assertThat(created.position()).isEqualTo(350);
        assertThat(created.title()).isEqualTo("new thing");
        assertThat(created.status()).isEqualTo("OPEN");
    }

    @Test
    void createOnAnEmptyDayStartsAt100() {
        when(items.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), DAY))
                .thenReturn(List.of());
        when(items.save(any(TodoItem.class))).thenAnswer(inv -> inv.getArgument(0));

        TodoItemView created = service.create(USER,
                new CreateCommand(DAY, "first", null, null, null, null));

        assertThat(created.position()).isEqualTo(100);
    }

    @Test
    void createRejectsABlankTitleWith400() {
        assertThatThrownBy(() -> service.create(USER, new CreateCommand(DAY, "   ", null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void reorderRewritesPositionsTo100200300InTheGivenOrder() {
        TodoItem a = itemOnDay(100);
        TodoItem b = itemOnDay(200);
        TodoItem c = itemOnDay(300);
        when(items.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), DAY))
                .thenReturn(List.of(a, b, c));

        List<TodoItemView> result = service.reorder(USER, DAY,
                List.of(c.getId(), a.getId(), b.getId()));

        assertThat(result).extracting(TodoItemView::id)
                .containsExactly(c.getId(), a.getId(), b.getId());
        assertThat(result).extracting(TodoItemView::position)
                .containsExactly(100, 200, 300);
    }

    @Test
    void reorderRejectsAMismatchedIdSetWith400() {
        TodoItem a = itemOnDay(100);
        TodoItem b = itemOnDay(200);
        when(items.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(USER.value(), DAY))
                .thenReturn(List.of(a, b));

        assertThatThrownBy(() -> service.reorder(USER, DAY, List.of(a.getId(), UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void completeTogglesStatusAndCompletedAt() {
        TodoItem item = itemOnDay(100);
        when(items.findByIdAndUserIdAndDeletedAtIsNull(item.getId(), USER.value()))
                .thenReturn(Optional.of(item));

        TodoItemView done = service.complete(USER, item.getId(), true);
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.completedAt()).isEqualTo(NOW);

        TodoItemView reopened = service.complete(USER, item.getId(), false);
        assertThat(reopened.status()).isEqualTo("OPEN");
        assertThat(reopened.completedAt()).isNull();
    }

    @Test
    void aForeignOrMissingRowIs404() {
        UUID id = UUID.randomUUID();
        when(items.findByIdAndUserIdAndDeletedAtIsNull(id, USER.value())).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.patch(USER, id, new PatchCommand("x", null, null, null, null, null)));

        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    void staleExpectedVersionIs409() {
        TodoItem item = itemOnDay(100); // version 0
        when(items.findByIdAndUserIdAndDeletedAtIsNull(item.getId(), USER.value()))
                .thenReturn(Optional.of(item));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.patch(USER, item.getId(),
                new PatchCommand(null, null, null, null, null, 7L)));

        assertThat(ex.getStatus().value()).isEqualTo(409);
    }

    @Test
    void patchAllowsOpenToCancelledButRejectsDoneToCancelled() {
        TodoItem open = itemOnDay(100);
        when(items.findByIdAndUserIdAndDeletedAtIsNull(eq(open.getId()), eq(USER.value())))
                .thenReturn(Optional.of(open));

        TodoItemView cancelled = service.patch(USER, open.getId(),
                new PatchCommand(null, null, null, null, "CANCELLED", null));
        assertThat(cancelled.status()).isEqualTo("CANCELLED");

        TodoItem done = itemOnDay(200);
        done.complete(NOW);
        when(items.findByIdAndUserIdAndDeletedAtIsNull(eq(done.getId()), eq(USER.value())))
                .thenReturn(Optional.of(done));

        assertThatThrownBy(() -> service.patch(USER, done.getId(),
                new PatchCommand(null, null, null, null, "CANCELLED", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }

    @Test
    void rangeRejectsAWindowWiderThanTheConfiguredMax() {
        assertThatThrownBy(() -> service.range(USER, DAY, DAY.plusDays(93)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(400));
    }
}
