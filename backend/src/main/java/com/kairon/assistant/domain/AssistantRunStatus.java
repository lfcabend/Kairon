package com.kairon.assistant.domain;

/** Lifecycle of an {@link AssistantRun} (docs/DESIGN.md §13.3). */
public enum AssistantRunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
