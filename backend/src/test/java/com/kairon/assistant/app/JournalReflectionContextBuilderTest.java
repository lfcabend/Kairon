package com.kairon.assistant.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.security.UserId;
import com.kairon.journal.api.JournalApi;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectsApi.ProjectPeriodStats;
import com.kairon.todo.api.TodoApi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalReflectionContextBuilderTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000c1"));
    private static final LocalDate START = LocalDate.of(2026, 9, 21);
    private static final LocalDate END = LocalDate.of(2026, 9, 27);

    @Mock
    JournalApi journal;

    @Mock
    TodoApi todos;

    @Mock
    ProjectsApi projects;

    private AssistantProperties properties;
    private JournalReflectionContextBuilder builder;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties(true, "sk-test-key", "claude-sonnet-5", null, 0, null, null, null,
                new AssistantProperties.JournalReflection(30, 5, null));
        builder = new JournalReflectionContextBuilder(journal, todos, projects, properties);
        org.mockito.Mockito.lenient().when(todos.periodStats(any(), any(), any()))
                .thenReturn(new TodoApi.PeriodStats(0, 0, 0));
        org.mockito.Mockito.lenient().when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of());
    }

    private static JournalEntryView entry(LocalDate day, String title, String content, Integer mood) {
        return new JournalEntryView(UUID.randomUUID(), day, 0, title, content, mood, Instant.now(), Instant.now(), 0);
    }

    @Test
    void build_rendersEveryEntryInFullWithDayAndMood() {
        when(journal.range(USER, START, END)).thenReturn(List.of(
                entry(START, null, "Felt good about the tiling progress.", 4),
                entry(START.plusDays(2), "Tiler call", "Had to reschedule again.", 2)));

        JournalReflectionContextBuilder.Context context = builder.build(USER, START, END, "balanced");

        assertThat(context.userContent()).contains("Felt good about the tiling progress.");
        assertThat(context.userContent()).contains("Had to reschedule again.");
        assertThat(context.userContent()).contains("Mood: 4/5");
        assertThat(context.userContent()).contains("Mood: 2/5");
        assertThat(context.userContent()).contains("\"Tiler call\"");
    }

    @Test
    void build_balancedTone_usesTheBalancedToneParagraph() {
        when(journal.range(any(), any(), any())).thenReturn(List.of(entry(START, null, "Content", null)));

        JournalReflectionContextBuilder.Context context = builder.build(USER, START, END, "balanced");

        assertThat(context.systemPrompt()).contains("warm but plain voice");
        assertThat(context.systemPrompt()).doesNotContain("genuine encouragement");
    }

    @Test
    void build_encouragingTone_usesTheEncouragingToneParagraph() {
        when(journal.range(any(), any(), any())).thenReturn(List.of(entry(START, null, "Content", null)));

        JournalReflectionContextBuilder.Context context = builder.build(USER, START, END, "encouraging");

        assertThat(context.systemPrompt()).contains("genuine encouragement");
    }

    @Test
    void build_directTone_usesTheDirectToneParagraph() {
        when(journal.range(any(), any(), any())).thenReturn(List.of(entry(START, null, "Content", null)));

        JournalReflectionContextBuilder.Context context = builder.build(USER, START, END, "direct");

        assertThat(context.systemPrompt()).contains("Write plainly and directly");
    }

    @Test
    void build_unrecognizedTone_fallsBackToBalanced() {
        when(journal.range(any(), any(), any())).thenReturn(List.of(entry(START, null, "Content", null)));

        JournalReflectionContextBuilder.Context context = builder.build(USER, START, END, "not-a-real-tone");

        assertThat(context.systemPrompt()).contains("warm but plain voice");
    }

    @Test
    void build_truncatesEntriesAtMaxEntriesOldestFirst() {
        properties = new AssistantProperties(true, "sk-test-key", "claude-sonnet-5", null, 0, null, null, null,
                new AssistantProperties.JournalReflection(2, 5, null));
        builder = new JournalReflectionContextBuilder(journal, todos, projects, properties);
        when(journal.range(any(), any(), any())).thenReturn(List.of(
                entry(START, null, "First", null),
                entry(START.plusDays(1), null, "Second", null),
                entry(START.plusDays(2), null, "Third", null)));

        JournalReflectionContextBuilder.Context context = builder.build(USER, START, END, "balanced");

        assertThat(context.userContent()).contains("First");
        assertThat(context.userContent()).contains("Second");
        assertThat(context.userContent()).doesNotContain("Third");
    }

    @Test
    void build_zeroTodoAndProjectActivity_rendersThePlainNoActivitySentence() {
        when(journal.range(any(), any(), any())).thenReturn(List.of(entry(START, null, "Content", null)));

        JournalReflectionContextBuilder.Context context = builder.build(USER, START, END, "balanced");

        assertThat(context.userContent()).contains("No todo or project activity is recorded for this week.");
    }

    @Test
    void build_withTodoAndProjectActivity_rendersTheGroundingSentence() {
        when(journal.range(any(), any(), any())).thenReturn(List.of(entry(START, null, "Content", null)));
        when(todos.periodStats(any(), any(), any())).thenReturn(new TodoApi.PeriodStats(12, 11, 3));
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of(
                new ProjectPeriodStats(UUID.randomUUID(), "Kitchen remodel", null, "Home", 4,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO),
                new ProjectPeriodStats(UUID.randomUUID(), "Website redesign", null, "Work", 2,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)));

        JournalReflectionContextBuilder.Context context = builder.build(USER, START, END, "balanced");

        assertThat(context.userContent()).contains("completed 11 of");
        assertThat(context.userContent()).contains("Kitchen remodel (4)");
        assertThat(context.userContent()).contains("Website redesign (2)");
        assertThat(context.userContent()).doesNotContain("No todo or project activity");
    }

    @Test
    void build_noStatsTableMethodExists() {
        // M10 D11 — unlike SummaryContextBuilder.Context, this Context has no
        // renderStatsTable(): the whole output is the model's own narrative.
        assertThat(JournalReflectionContextBuilder.Context.class.getMethods())
                .extracting(m -> m.getName())
                .doesNotContain("renderStatsTable");
    }
}
