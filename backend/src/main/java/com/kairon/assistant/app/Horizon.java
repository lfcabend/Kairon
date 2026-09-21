package com.kairon.assistant.app;

/**
 * How far a {@code TODO_SUGGESTION} run may spread its suggestions
 * (docs/milestones/M8-assistant-foundations.md D10).
 */
public enum Horizon {
    /** Every suggestion's {@code suggestedForDay} must equal the requested day. */
    DAY,
    /** Suggestions may spread across {@code [day, day + 6]}. */
    WEEK
}
