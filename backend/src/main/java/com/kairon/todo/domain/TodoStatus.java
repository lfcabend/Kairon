package com.kairon.todo.domain;

/**
 * Lifecycle of a {@link TodoItem}. {@code OPEN} items are the working set for a
 * day; {@code DONE} carries a {@code completedAt}; {@code CANCELLED} is used both
 * for an explicit "won't do" and for the source items a rollover carries forward
 * (per docs/DATA_MODEL.md: cancel the old, create the new).
 */
public enum TodoStatus {
    OPEN,
    DONE,
    CANCELLED
}
