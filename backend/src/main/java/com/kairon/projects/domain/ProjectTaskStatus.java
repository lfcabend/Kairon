package com.kairon.projects.domain;

/** Lifecycle of a {@link ProjectTask}. New tasks start {@code TODO}. */
public enum ProjectTaskStatus {
    TODO,
    IN_PROGRESS,
    BLOCKED,
    DONE
}
