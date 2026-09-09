package com.kairon.todo.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.todo.domain.TodoItem;
import com.kairon.todo.domain.TodoStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoItemRepository extends JpaRepository<TodoItem, UUID> {

    List<TodoItem> findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(
            UUID userId, LocalDate day);

    List<TodoItem> findByUserIdAndDayBetweenAndDeletedAtIsNullOrderByDayAscPositionAsc(
            UUID userId, LocalDate from, LocalDate to);

    Optional<TodoItem> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    // Rollover preview / sweep: every non-deleted item with the given status on a
    // day strictly before `onDay` and no earlier than `notBefore`, oldest day
    // first. The service groups the result by `day` for the preview and carries
    // the whole set on rollover.
    List<TodoItem>
            findByUserIdAndStatusAndDayLessThanAndDayGreaterThanEqualAndDeletedAtIsNullOrderByDayAscPositionAsc(
                    UUID userId, TodoStatus status, LocalDate onDay, LocalDate notBefore);
}
