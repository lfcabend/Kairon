package com.kairon.assistant.app;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import com.kairon.assistant.domain.AssistantRunKind;

/**
 * Which kind of execution summary is being requested, and how it resolves a
 * reference date to the period it covers. {@link #WEEK} resolves to the
 * Monday–Sunday ISO week containing the date; {@link #MONTH} resolves to that
 * date's calendar month. Added for M9 (docs/milestones/M9-execution-summaries.md D8).
 */
public enum SummaryPeriod {
    WEEK,
    MONTH;

    /** {@code date}'s containing week (Mon–Sun) or calendar month, as an inclusive range. */
    public Range resolve(LocalDate date) {
        return switch (this) {
            case WEEK -> {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Range(start, start.plusDays(6));
            }
            case MONTH -> new Range(
                    date.with(TemporalAdjusters.firstDayOfMonth()),
                    date.with(TemporalAdjusters.lastDayOfMonth()));
        };
    }

    /** The one place the {@link SummaryPeriod} → {@link AssistantRunKind} mapping lives. */
    public AssistantRunKind kind() {
        return switch (this) {
            case WEEK -> AssistantRunKind.WEEKLY_SUMMARY;
            case MONTH -> AssistantRunKind.MONTHLY_SUMMARY;
        };
    }

    public record Range(LocalDate start, LocalDate end) {
    }
}
