package com.kairon.assistant.domain;

/**
 * What an {@link AssistantRun} was for. {@link #TODO_SUGGESTION} (M8) and
 * {@link #PROJECT_GENERATION} (M8.5) are produced today; {@link #WEEKLY_SUMMARY}/
 * {@link #MONTHLY_SUMMARY}/{@link #JOURNAL_REFLECTION} exist so M9/M10 need no
 * migration to widen the schema's check constraint
 * (docs/milestones/M8-assistant-foundations.md D2). {@code PROJECT_GENERATION}
 * itself needed a real migration (V008) since it wasn't among the kinds M8's
 * V007 anticipated (docs/milestones/M8.5-project-generation.md D1).
 */
public enum AssistantRunKind {
    TODO_SUGGESTION,
    WEEKLY_SUMMARY,
    MONTHLY_SUMMARY,
    JOURNAL_REFLECTION,
    PROJECT_GENERATION
}
