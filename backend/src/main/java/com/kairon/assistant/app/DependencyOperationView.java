package com.kairon.assistant.app;

import java.util.UUID;

/** One dependency change in a proposed edit diff — {@code op} is {@code ADD} or {@code REMOVE}. */
public record DependencyOperationView(
        String op,
        UUID existingDependencyId,
        String predecessorRef,
        String successorRef,
        String type,
        Integer lagDays) {
}
