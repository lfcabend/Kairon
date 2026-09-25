package com.kairon.assistant.app;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One task in a proposed plan, keyed for cross-reference (M8.5 D6). */
public record PlannedTaskView(
        String key,
        String parentKey,
        String name,
        String description,
        boolean isMilestone,
        LocalDate plannedStart,
        LocalDate plannedEnd,
        BigDecimal estimateHours) {
}
