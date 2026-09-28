package com.kairon.todo.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kairon.todo.api.TodoApi;
import com.kairon.todo.domain.TodoItem;
import com.kairon.todo.domain.TodoStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // Added for M9's execution-summary aggregation (docs/milestones/M9-execution-summaries.md D4).
    // `created` = originally entered (no rollover source); `rolledOver` = carried in from an earlier
    // day; `completed` = DONE with completedAt in the corresponding UTC instant range for [from, to].
    @Query("""
            SELECT new com.kairon.todo.api.TodoApi$PeriodStats(
                COALESCE(SUM(CASE WHEN t.rolledOverFromId IS NULL THEN 1L ELSE 0L END), 0),
                COALESCE(SUM(CASE WHEN t.status = com.kairon.todo.domain.TodoStatus.DONE
                              AND t.completedAt >= :fromInstant AND t.completedAt < :toInstantExclusive
                         THEN 1L ELSE 0L END), 0),
                COALESCE(SUM(CASE WHEN t.rolledOverFromId IS NOT NULL THEN 1L ELSE 0L END), 0))
            FROM TodoItem t
            WHERE t.userId = :userId AND t.deletedAt IS NULL AND t.day BETWEEN :from AND :to
            """)
    TodoApi.PeriodStats periodStats(@Param("userId") UUID userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("fromInstant") Instant fromInstant,
            @Param("toInstantExclusive") Instant toInstantExclusive);
}
