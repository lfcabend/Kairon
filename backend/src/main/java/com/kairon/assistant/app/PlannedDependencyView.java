package com.kairon.assistant.app;

/** One dependency edge in a proposed plan, referencing tasks by key (M8.5 D6). */
public record PlannedDependencyView(String predecessorKey, String successorKey, String type, Integer lagDays) {
}
