package com.kairon.assistant.app;

import java.time.LocalDate;

import com.kairon.assistant.domain.AssistantRunKind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryPeriodTest {

    @Test
    void week_resolvesToTheMondayToSundayContainingTheDate() {
        // Sep 21, 2026 is a Monday.
        SummaryPeriod.Range range = SummaryPeriod.WEEK.resolve(LocalDate.of(2026, 9, 21));
        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    @Test
    void week_resolvesTheSameRangeFromAnyDayInThatWeek() {
        // Sep 27, 2026 is the Sunday of the same week as the test above.
        SummaryPeriod.Range range = SummaryPeriod.WEEK.resolve(LocalDate.of(2026, 9, 27));
        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    @Test
    void week_mondayFiring_summarizesTheWeekThatJustEnded() {
        // Scheduler fires Monday Sep 28; "yesterday" is Sunday Sep 27, the last day
        // of the week that just ended (D8).
        LocalDate yesterday = LocalDate.of(2026, 9, 28).minusDays(1);
        SummaryPeriod.Range range = SummaryPeriod.WEEK.resolve(yesterday);
        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    @Test
    void month_resolvesToTheCalendarMonthContainingTheDate() {
        SummaryPeriod.Range range = SummaryPeriod.MONTH.resolve(LocalDate.of(2026, 9, 15));
        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void month_firstOfMonthFiring_summarizesTheMonthThatJustEnded() {
        // Scheduler fires Oct 1; "yesterday" is Sep 30, the last day of September.
        LocalDate yesterday = LocalDate.of(2026, 10, 1).minusDays(1);
        SummaryPeriod.Range range = SummaryPeriod.MONTH.resolve(yesterday);
        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void month_leapFebruary_resolvesToFeb29() {
        SummaryPeriod.Range range = SummaryPeriod.MONTH.resolve(LocalDate.of(2028, 2, 10));
        assertThat(range.start()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void kind_mapsWeekAndMonthToTheirAssistantRunKind() {
        assertThat(SummaryPeriod.WEEK.kind()).isEqualTo(AssistantRunKind.WEEKLY_SUMMARY);
        assertThat(SummaryPeriod.MONTH.kind()).isEqualTo(AssistantRunKind.MONTHLY_SUMMARY);
    }
}
