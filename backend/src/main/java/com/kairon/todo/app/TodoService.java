package com.kairon.todo.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.todo.api.TodoApi;
import com.kairon.todo.api.TodoItemView;
import com.kairon.todo.domain.TodoItem;
import com.kairon.todo.domain.TodoStatus;
import com.kairon.todo.repo.TodoItemRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The todo module's application service: day/range reads, CRUD, complete-toggle
 * and reorder. Every method takes a {@link UserId} and scopes its queries by it;
 * a missing or foreign row is a 404, never a 403 (docs/DESIGN.md §3.2).
 *
 * <p>Positions are sparse ({@code 100, 200, 300, …}) so a single move rarely has
 * to renumber the whole list; {@code :reorder} rewrites them from scratch.
 */
@Service
public class TodoService implements TodoApi {

    private static final int POSITION_GAP = 100;

    private final TodoItemRepository items;
    private final TodoProperties properties;
    private final Clock clock;

    public TodoService(TodoItemRepository items, TodoProperties properties, Clock clock) {
        this.items = items;
        this.properties = properties;
        this.clock = clock;
    }

    public record CreateCommand(
            LocalDate day,
            String title,
            String notes,
            Integer priority,
            Integer estimateMinutes,
            UUID sourceProjectTaskId) {
    }

    /** Null fields are left unchanged. {@code status} drives an allowed transition. */
    public record PatchCommand(
            String title,
            String notes,
            Integer priority,
            Integer estimateMinutes,
            String status,
            Long expectedVersion) {
    }

    @Transactional(readOnly = true)
    public List<TodoItemView> list(UserId userId, LocalDate day) {
        return items
                .findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(userId.value(), day)
                .stream()
                .map(TodoMapper::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TodoItemView> range(UserId userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("`to` must not be before `from`.");
        }
        if (ChronoUnit.DAYS.between(from, to) > properties.rangeMaxDays()) {
            throw ApiException.badRequest(
                    "Date range must not exceed " + properties.rangeMaxDays() + " days.");
        }
        return items
                .findByUserIdAndDayBetweenAndDeletedAtIsNullOrderByDayAscPositionAsc(
                        userId.value(), from, to)
                .stream()
                .map(TodoMapper::toView)
                .toList();
    }

    @Transactional
    public TodoItemView create(UserId userId, CreateCommand command) {
        String title = requireTitle(command.title());
        int position = nextPosition(userId.value(), command.day());
        TodoItem item = TodoItem.create(
                userId.value(),
                command.day(),
                title,
                trimToNull(command.notes()),
                command.priority() == null ? 0 : command.priority(),
                position,
                command.estimateMinutes(),
                command.sourceProjectTaskId());
        return TodoMapper.toView(items.save(item));
    }

    @Transactional
    public TodoItemView patch(UserId userId, UUID id, PatchCommand command) {
        TodoItem item = require(userId, id);
        if (command.expectedVersion() != null && command.expectedVersion() != item.getVersion()) {
            throw ApiException.conflict(
                    "This item was modified by another request. Reload and try again.");
        }
        if (command.title() != null) {
            item.rename(requireTitle(command.title()));
        }
        if (command.notes() != null) {
            item.editNotes(trimToNull(command.notes()));
        }
        if (command.priority() != null) {
            item.reprioritise(command.priority());
        }
        if (command.estimateMinutes() != null) {
            item.estimate(command.estimateMinutes() == 0 ? null : command.estimateMinutes());
        }
        if (command.status() != null) {
            applyStatus(item, command.status());
        }
        return TodoMapper.toView(item);
    }

    private void applyStatus(TodoItem item, String raw) {
        TodoStatus target;
        try {
            target = TodoStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown status: " + raw);
        }
        TodoStatus from = item.getStatus();
        if (from == target) {
            return;
        }
        switch (target) {
            case DONE -> {
                if (from != TodoStatus.OPEN) {
                    throw badTransition(from, target);
                }
                item.complete(clock.instant());
            }
            case OPEN -> item.reopen();
            case CANCELLED -> {
                if (from != TodoStatus.OPEN) {
                    throw badTransition(from, target);
                }
                item.cancel();
            }
        }
    }

    @Transactional
    public TodoItemView complete(UserId userId, UUID id, boolean complete) {
        TodoItem item = require(userId, id);
        if (complete) {
            item.complete(clock.instant());
        } else {
            item.reopen();
        }
        return TodoMapper.toView(item);
    }

    @Transactional
    public List<TodoItemView> reorder(UserId userId, LocalDate day, List<UUID> orderedIds) {
        List<TodoItem> current = items
                .findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(userId.value(), day);
        Map<UUID, TodoItem> byId = new HashMap<>();
        for (TodoItem item : current) {
            byId.put(item.getId(), item);
        }
        if (orderedIds.size() != byId.size() || !byId.keySet().equals(new java.util.HashSet<>(orderedIds))) {
            throw ApiException.badRequest(
                    "`orderedIds` must list exactly the non-deleted items for that day.");
        }
        int position = POSITION_GAP;
        List<TodoItemView> result = new java.util.ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            TodoItem item = byId.get(id);
            item.moveTo(position);
            result.add(TodoMapper.toView(item));
            position += POSITION_GAP;
        }
        return result;
    }

    @Transactional
    public void softDelete(UserId userId, UUID id) {
        require(userId, id).softDelete(clock.instant());
    }

    // --- TodoApi port ----------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TodoItemView> forDay(UserId userId, LocalDate day) {
        return list(userId, day);
    }

    @Override
    @Transactional
    public TodoItemView create(UserId userId, NewTodo command) {
        return create(userId, new CreateCommand(
                command.day(),
                command.title(),
                command.notes(),
                command.priority(),
                command.estimateMinutes(),
                command.sourceProjectTaskId()));
    }

    // --- internals -----------------------------------------------------------

    private TodoItem require(UserId userId, UUID id) {
        return items.findByIdAndUserIdAndDeletedAtIsNull(id, userId.value())
                .orElseThrow(() -> ApiException.notFound("Todo item not found."));
    }

    private int nextPosition(UUID userId, LocalDate day) {
        return items.findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(userId, day)
                .stream()
                .mapToInt(TodoItem::getPosition)
                .max()
                .orElse(0) + POSITION_GAP;
    }

    private static ApiException badTransition(TodoStatus from, TodoStatus to) {
        return ApiException.badRequest("Cannot move a todo item from " + from + " to " + to + ".");
    }

    private static String requireTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("Title must not be blank.");
        }
        if (trimmed.length() > 500) {
            throw ApiException.badRequest("Title must be at most 500 characters.");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
