package com.kairon.assistant.app;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.kairon.common.security.UserId;
import com.kairon.journal.api.JournalApi;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.todo.api.TodoApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds the system prompt and user content for a {@code JOURNAL_REFLECTION}
 * run. Three data sources (docs/milestones/M10-journal-reflection.md D10):
 * the week's journal entries in full (the point of the feature), plus light
 * todo/project grounding — deliberately narrower than {@link SummaryContextBuilder}'s
 * five-source aggregation, since this feature doesn't need a risk/attention
 * analysis, only enough context to relate a journal entry to something
 * concrete. No stats table (D11) — the entire output is the model's own
 * narrative.
 */
@Component
class JournalReflectionContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(JournalReflectionContextBuilder.class);

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter ENTRY_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    // Kept in sync with docs/milestones/M10-journal-reflection.md §4.5 — that doc
    // is the source of truth for *why* this prompt is shaped this way.
    private static final String SHARED_PROMPT = """
            You are Kairon's journal-reflection assistant. You are given one week's
            journal entries in full, plus a little context about what else happened that
            week (todos and project work). Reflect on the week back to the user — you are
            not summarizing it and not giving productivity advice; you are helping them
            see their own week more clearly.

            Base everything only on the journal entries and context given below. Never
            invent an event, feeling, or detail that isn't in the text. If an entry is
            short or sparse, don't pad your reflection to compensate — a quiet week can
            get a short, honest reflection.

            %s

            Structure your response as markdown with these sections:
            - A short, specific opening line naming the week's overall shape — don't
              open with a generic "this week you..." if the entries support something
              more specific.
            - "Patterns" — anything that recurs across two or more entries (a recurring
              worry, a repeated subject, a mood that shows up more than once). Reference
              the mood values you're given directly when they support a point; don't
              invent a trend from entries that don't actually show one.
            - "Worth noticing" — one or two things the entries suggest the user might not
              have fully named themselves — a blind spot, a tension, something glossed
              over. Frame as an observation or a question, never a directive.
            - "A question to sit with" — exactly one open-ended question grounded in
              something specific from this week's entries, not a generic journaling
              prompt.

            If the todo/project context given below genuinely connects to something in
            the journal entries (e.g. an entry mentions a project also shown as having
            completed work), you may mention the connection — but never force one, and
            never treat the todo/project numbers as the main subject. The journal
            entries are the subject; the numbers are only grounding.

            Keep the whole response under about 350 words.""";

    private static final String TONE_BALANCED = """
            Write in a warm but plain voice — acknowledge what's genuinely going well and
            name what's genuinely difficult, without overstating either. Avoid both empty
            positivity and bluntness for its own sake.""";

    private static final String TONE_ENCOURAGING = """
            Write with warmth and genuine encouragement — notice effort and progress even
            where the outcome fell short, and frame difficulties gently. Stay honest;
            encouragement here means generous framing of what's true, never inventing
            progress that didn't happen.""";

    private static final String TONE_DIRECT = """
            Write plainly and directly — name what the entries show without softening it,
            including anything uncomfortable. Skip reassurance and hedging; trust the
            user to sit with a direct observation.""";

    private final JournalApi journal;
    private final TodoApi todos;
    private final ProjectsApi projects;
    private final AssistantProperties properties;

    JournalReflectionContextBuilder(JournalApi journal, TodoApi todos, ProjectsApi projects,
            AssistantProperties properties) {
        this.journal = journal;
        this.todos = todos;
        this.projects = projects;
        this.properties = properties;
    }

    record Context(String systemPrompt, String userContent, Map<String, Object> inputSnapshot) {
    }

    Context build(UserId userId, LocalDate weekStart, LocalDate weekEnd, String tone) {
        int maxEntries = properties.journalReflection().maxEntries();
        int maxProjects = properties.journalReflection().maxProjects();

        List<JournalEntryView> entries = journal.range(userId, weekStart, weekEnd);
        List<JournalEntryView> capped = entries;
        if (entries.size() > maxEntries) {
            log.warn("Journal reflection context: {} entries exceeds max-entries {}, truncating (oldest-first)",
                    entries.size(), maxEntries);
            capped = entries.subList(0, maxEntries);
        }

        TodoApi.PeriodStats todoStats = todos.periodStats(userId, weekStart, weekEnd);
        List<ProjectsApi.ProjectPeriodStats> topProjects = projects.projectPeriodStats(userId, weekStart, weekEnd)
                .stream()
                .sorted(Comparator.comparingLong(ProjectsApi.ProjectPeriodStats::tasksCompleted).reversed())
                .limit(maxProjects)
                .toList();

        String systemPrompt = SHARED_PROMPT.formatted(toneParagraph(tone));
        String userContent = renderUserContent(weekStart, weekEnd, todoStats, topProjects, capped);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("systemPrompt", systemPrompt);
        snapshot.put("userContent", userContent);
        return new Context(systemPrompt, userContent, snapshot);
    }

    private static String toneParagraph(String tone) {
        return switch (tone) {
            case "encouraging" -> TONE_ENCOURAGING;
            case "direct" -> TONE_DIRECT;
            default -> TONE_BALANCED; // "balanced" and any unrecognized value
        };
    }

    private String renderUserContent(LocalDate weekStart, LocalDate weekEnd, TodoApi.PeriodStats todoStats,
            List<ProjectsApi.ProjectPeriodStats> topProjects, List<JournalEntryView> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("Weekly journal reflection for ").append(periodLabel(weekStart, weekEnd)).append(".\n\n");

        boolean noTodoActivity = todoStats.created() == 0 && todoStats.completed() == 0
                && todoStats.rolledOver() == 0;
        if (noTodoActivity && topProjects.isEmpty()) {
            sb.append("No todo or project activity is recorded for this week.\n\n");
        } else {
            long onDocket = todoStats.created() + todoStats.rolledOver();
            if (onDocket > 0) {
                sb.append("This week you completed ").append(todoStats.completed()).append(" of ")
                        .append(onDocket).append(" todo items on your docket (")
                        .append(Math.round(todoStats.completionRate() * 100)).append("%)");
                if (todoStats.rolledOver() > 0) {
                    sb.append(", with ").append(todoStats.rolledOver()).append(" rolled over from an earlier day");
                }
                sb.append(".\n");
            }
            if (!topProjects.isEmpty()) {
                sb.append("Project work with completed tasks this week: ");
                sb.append(topProjects.stream()
                        .map(p -> p.projectName() + " (" + p.tasksCompleted() + ")")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(""));
                sb.append(".\n");
            }
            sb.append('\n');
        }

        sb.append("Journal entries this week:\n\n");
        for (JournalEntryView entry : entries) {
            sb.append('[').append(entry.day().format(ENTRY_DAY)).append(']');
            if (entry.title() != null && !entry.title().isBlank()) {
                sb.append(" \"").append(entry.title()).append('"').append(" —");
            }
            if (entry.mood() != null) {
                sb.append(" Mood: ").append(entry.mood()).append("/5");
            }
            sb.append('\n').append(entry.content()).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }

    private String periodLabel(LocalDate start, LocalDate end) {
        if (start.getYear() == end.getYear()) {
            return start.format(MONTH_DAY) + "–" + end.format(MONTH_DAY_YEAR);
        }
        return start.format(MONTH_DAY_YEAR) + "–" + end.format(MONTH_DAY_YEAR);
    }
}
