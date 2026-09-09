package com.kairon.todo.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.todo.api.TodoItemView;
import com.kairon.todo.domain.TodoItem;
import com.kairon.todo.domain.TodoStatus;
import com.kairon.todo.repo.TodoItemRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carries unfinished ({@code OPEN}) todo items forward. Because the list is often
 * skipped for days at a time, a rollover <em>sweeps</em> every past day that
 * still has open items inside a bounded look-back window rather than only
 * "yesterday" (docs/milestones/M2 D4). Per docs/DATA_MODEL.md the operation
 * <em>cancels the old and creates the new</em>, so each day's list reflects
 * intent for that day and the carry chain stays traceable via
 * {@code rolledOverFromId}.
 */
@Service
public class RolloverService {

    private static final int POSITION_GAP = 100;

    private final TodoItemRepository items;
    private final TodoProperties properties;
    private final Clock clock;

    public RolloverService(TodoItemRepository items, TodoProperties properties, Clock clock) {
        this.items = items;
        this.properties = properties;
        this.clock = clock;
    }

    public record RolloverCommand(LocalDate toDay, LocalDate fromDay, List<UUID> ids) {
    }

    public record UndoCommand(List<UUID> createdIds) {
    }

    public record SourceDay(LocalDate day, List<TodoItemView> items) {
    }

    public record RolloverPreview(List<SourceDay> sourceDays, int totalItems) {
    }

    /**
     * Every eligible {@code OPEN} item in {@code [onDay - lookBack, onDay)},
     * grouped by source day, oldest first. An empty {@code sourceDays} means
     * there is nothing to carry.
     */
    @Transactional(readOnly = true)
    public RolloverPreview preview(UserId userId, LocalDate onDay) {
        List<TodoItem> eligible = sweep(userId.value(), onDay, null);
        Map<LocalDate, List<TodoItemView>> byDay = new LinkedHashMap<>();
        for (TodoItem item : eligible) {
            byDay.computeIfAbsent(item.getDay(), d -> new ArrayList<>())
                    .add(TodoMapper.toView(item));
        }
        List<SourceDay> sourceDays = byDay.entrySet().stream()
                .map(e -> new SourceDay(e.getKey(), e.getValue()))
                .toList();
        return new RolloverPreview(sourceDays, eligible.size());
    }

    /**
     * Rolls the resolved set onto {@code command.toDay()}. With {@code ids} given
     * only those items are carried (each validated {@code OPEN}, owned, and dated
     * before {@code toDay}); otherwise every eligible {@code OPEN} item in the
     * look-back window is swept, optionally narrowed to a single {@code fromDay}.
     * New items are appended in source-day, then source-position order.
     */
    @Transactional
    public List<TodoItemView> rollover(UserId userId, RolloverCommand command) {
        LocalDate toDay = command.toDay();
        if (toDay == null) {
            throw ApiException.badRequest("`toDay` is required.");
        }
        List<TodoItem> sources = command.ids() != null && !command.ids().isEmpty()
                ? resolveExplicit(userId.value(), toDay, command.ids())
                : sweep(userId.value(), toDay, command.fromDay());

        int position = nextPosition(userId.value(), toDay);
        List<TodoItemView> created = new ArrayList<>(sources.size());
        for (TodoItem source : sources) {
            TodoItem carried = TodoItem.rolledFrom(source, toDay, position);
            source.cancel();
            created.add(TodoMapper.toView(items.save(carried)));
            position += POSITION_GAP;
        }
        return created;
    }

    /**
     * Reverses an {@code auto}-mode sweep: reopens each still-present created
     * item's source and soft-deletes the created item. Only touches rows owned by
     * the user.
     */
    @Transactional
    public List<TodoItemView> undo(UserId userId, UndoCommand command) {
        List<TodoItemView> reopened = new ArrayList<>();
        if (command.createdIds() == null) {
            return reopened;
        }
        for (UUID createdId : command.createdIds()) {
            TodoItem created = items
                    .findByIdAndUserIdAndDeletedAtIsNull(createdId, userId.value())
                    .orElse(null);
            if (created == null || created.getRolledOverFromId() == null) {
                continue;
            }
            items.findByIdAndUserIdAndDeletedAtIsNull(created.getRolledOverFromId(), userId.value())
                    .ifPresent(source -> {
                        source.reopen();
                        reopened.add(TodoMapper.toView(source));
                    });
            created.softDelete(clock.instant());
        }
        return reopened;
    }

    // --- internals -----------------------------------------------------------

    private List<TodoItem> sweep(UUID userId, LocalDate onDay, LocalDate fromDay) {
        LocalDate notBefore = onDay.minusDays(properties.rolloverLookBackDays());
        List<TodoItem> eligible = items
                .findByUserIdAndStatusAndDayLessThanAndDayGreaterThanEqualAndDeletedAtIsNullOrderByDayAscPositionAsc(
                        userId, TodoStatus.OPEN, onDay, notBefore);
        if (fromDay != null) {
            eligible = eligible.stream().filter(i -> i.getDay().equals(fromDay)).toList();
        }
        return eligible;
    }

    private List<TodoItem> resolveExplicit(UUID userId, LocalDate toDay, List<UUID> ids) {
        List<TodoItem> resolved = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            TodoItem item = items.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                    .orElseThrow(() -> ApiException.notFound("Todo item not found."));
            if (item.getStatus() != TodoStatus.OPEN) {
                throw ApiException.badRequest("Only OPEN items can be rolled over.");
            }
            if (!item.getDay().isBefore(toDay)) {
                throw ApiException.badRequest("Items to roll over must be dated before `toDay`.");
            }
            resolved.add(item);
        }
        resolved.sort((a, b) -> {
            int byDay = a.getDay().compareTo(b.getDay());
            return byDay != 0 ? byDay : Integer.compare(a.getPosition(), b.getPosition());
        });
        return resolved;
    }

    private int nextPosition(UUID userId, LocalDate day) {
        return items.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(userId, day)
                .stream()
                .mapToInt(TodoItem::getPosition)
                .max()
                .orElse(0) + POSITION_GAP;
    }
}
