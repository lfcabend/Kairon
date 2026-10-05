package com.kairon.assistant.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One task change in a proposed edit diff — {@code op} is {@code ADD}, {@code UPDATE}, or {@code REMOVE}. */
public record TaskOperationView(
        String op,
        UUID existingTaskId,
        String key,
        String parentRef,
        String name,
        String description,
        boolean isMilestone,
        LocalDate plannedStart,
        LocalDate plannedEnd,
        BigDecimal estimateHours) {
}
