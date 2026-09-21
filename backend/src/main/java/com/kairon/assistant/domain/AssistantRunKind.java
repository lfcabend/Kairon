package com.kairon.assistant.domain;

/**
 * What an {@link AssistantRun} was for. Only {@link #TODO_SUGGESTION} is
 * produced as of M8; the other three exist so M9/M10 need no migration to
 * widen the schema's check constraint (docs/milestones/M8-assistant-foundations.md D2).
 */
public enum AssistantRunKind {
    TODO_SUGGESTION,
    WEEKLY_SUMMARY,
    MONTHLY_SUMMARY,
    JOURNAL_REFLECTION
}
