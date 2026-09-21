package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.kairon.common.security.UserId;
import com.kairon.journal.api.JournalApi;
import com.kairon.journal.api.JournalEntryView;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.todo.api.TodoApi;
import com.kairon.todo.api.TodoItemView;

import org.springframework.stereotype.Component;

/**
 * Builds the system prompt and per-request user content for a
 * {@code TODO_SUGGESTION} run from the D9/D9a context sources. The system
 * prompt is a fixed constant — no per-request data — so it stays cacheable
 * (docs/milestones/M8-assistant-foundations.md §4.5); every varying fact (the
 * date window, the gathered data, the computed signals) lives in the user
 * content instead.
 */
@Component
class TodoSuggestionContextBuilder {

    // Kept in sync with docs/milestones/M8-assistant-foundations.md §4.5 — that
    // doc is the source of truth for *why* this prompt is shaped this way.
    private static final String SYSTEM_PROMPT = """
            You are Kairon's todo-planning assistant. You help the user plan concrete next
            actions for their day(s) — you are not the user, and you never address them as
            if you were staff or a project manager reporting up.

            Base every suggestion only on the context given in the user message below
            (their recent todo history, open project tasks, and recent journal notes). Do
            not invent work that isn't grounded in that context.

            Rules:
            - Keep each item small and concrete — something completable in one sitting,
              not a vague goal. "Order cabinet hardware," not "Make progress on kitchen
              remodel."
            - Never duplicate a todo already listed for the requested day.
            - Never invent a deadline or urgency that isn't in the source data. If a task
              has no date, don't imply one.
            - Prefer tasks that are due, overdue, blocked-then-unblocked, or stalled over
              routine or low-value ones.
            - The user message tells you how many items they already have open today and
              their recent daily completion pace. Bias toward fewer, higher-value
              suggestions when today is already busy relative to that pace — but only
              suggest what genuinely clears the bar above; never pad the list to hit a
              target count, and never withhold a suggestion that clearly earns its place
              just because the day looks full.
            - suggestedForDay must fall within the date range stated in the user message.
            - Set sourceProjectTaskId when a suggestion maps directly to an existing open
              project task; leave it unset otherwise (e.g. something inferred only from a
              journal entry).
            - If nothing in the context warrants a suggestion, return an empty list.

            Example. Given: 2 items already open today, a 7-day pace of 2.3 items/day; an
            overdue "Order cabinet hardware" task (Kitchen remodel); an open "Write
            homepage copy" task due in three weeks (Website redesign); a journal entry
            from two days ago: "need to text the tiler to confirm his week." A good
            response suggests exactly two items: "Order cabinet hardware" (rationale:
            overdue kitchen-remodel task, unblocked; sourceProjectTaskId set to that
            task's id) and "Text the tiler to confirm his week" (rationale: mentioned in
            your Sep 22 journal entry; sourceProjectTaskId unset — no matching project
            task exists). It does NOT suggest "Write homepage copy": open, but not due,
            overdue, or stalled, and today's already at pace — a task simply existing
            isn't reason enough to surface it.""";

    private final TodoApi todos;
    private final JournalApi journal;
    private final ProjectsApi projects;
    private final AssistantProperties properties;

    TodoSuggestionContextBuilder(TodoApi todos, JournalApi journal, ProjectsApi projects,
            AssistantProperties properties) {
        this.todos = todos;
        this.journal = journal;
        this.projects = projects;
        this.properties = properties;
    }

    record Context(String systemPrompt, String userContent, Map<String, Object> inputSnapshot) {
    }

    Context build(UserId userId, LocalDate day, Horizon horizon) {
        AssistantProperties.TodoSuggestions cfg = properties.todoSuggestions();
        LocalDate periodEnd = horizon == Horizon.WEEK ? day.plusDays(6) : day;

        List<TodoItemView> todaysItems = todos.forDay(userId, day);
        List<TodoItemView> history = todos.range(userId, day.minusDays(cfg.historyDays()), day.minusDays(1));
        List<ProjectTaskView> dueOrOverdue = projects.dueOrOverdue(userId, day);
        Set<java.util.UUID> dueIds = dueOrOverdue.stream().map(ProjectTaskView::id).collect(Collectors.toSet());
        List<ProjectTaskView> otherOpenTasks = projects.openTasksInActiveProjects(userId).stream()
                .filter(t -> !dueIds.contains(t.id()))
                .toList();
        List<JournalEntryView> recentJournal = journal
                .range(userId, day.minusDays(cfg.journalLookbackDays()), day)
                .stream()
                .sorted(Comparator.comparing(JournalEntryView::day).reversed()
                        .thenComparing(JournalEntryView::createdAt, Comparator.reverseOrder()))
                .limit(cfg.journalEntryLimit())
                .toList();

        long openToday = todaysItems.stream().filter(t -> "OPEN".equals(t.status())).count();
        long doneInHistory = history.stream().filter(t -> "DONE".equals(t.status())).count();
        double avgCompletedPerDay = Math.round((doneInHistory / (double) cfg.historyDays()) * 10) / 10.0;

        String userContent = renderUserContent(day, periodEnd, openToday, avgCompletedPerDay,
                todaysItems, history, dueOrOverdue, otherOpenTasks, recentJournal);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("systemPrompt", SYSTEM_PROMPT);
        snapshot.put("userContent", userContent);
        return new Context(SYSTEM_PROMPT, userContent, snapshot);
    }

    private String renderUserContent(LocalDate day, LocalDate periodEnd, long openToday,
            double avgCompletedPerDay, List<TodoItemView> todaysItems, List<TodoItemView> history,
            List<ProjectTaskView> dueOrOverdue, List<ProjectTaskView> otherOpenTasks,
            List<JournalEntryView> recentJournal) {
        StringBuilder sb = new StringBuilder();
        if (periodEnd.equals(day)) {
            sb.append("Suggest todos for ").append(day)
                    .append(". Only suggestedForDay = ").append(day).append(" is valid.\n\n");
        } else {
            sb.append("Suggest todos for the week starting ").append(day)
                    .append(". Only suggestedForDay values between ").append(day)
                    .append(" and ").append(periodEnd).append(" are valid.\n\n");
        }
        sb.append("You already have ").append(openToday).append(" open item(s) today. ")
                .append("Over the last few days you completed an average of ")
                .append(avgCompletedPerDay).append(" items/day.\n\n");

        appendTodoSection(sb, "Today's open items", todaysItems);
        appendTodoSection(sb, "Recent todo history", history);
        appendTaskSection(sb, "Due or overdue project tasks", dueOrOverdue);
        appendTaskSection(sb, "Other open tasks in active/on-hold projects", otherOpenTasks);
        appendJournalSection(sb, recentJournal);
        return sb.toString();
    }

    private void appendTodoSection(StringBuilder sb, String heading, List<TodoItemView> items) {
        sb.append(heading).append(":\n");
        if (items.isEmpty()) {
            sb.append("- (none)\n\n");
            return;
        }
        for (TodoItemView t : items) {
            sb.append("- [").append(t.status()).append("] ").append(t.title()).append(" (").append(t.day())
                    .append(")\n");
        }
        sb.append('\n');
    }

    private void appendTaskSection(StringBuilder sb, String heading, List<ProjectTaskView> tasks) {
        sb.append(heading).append(":\n");
        if (tasks.isEmpty()) {
            sb.append("- (none)\n\n");
            return;
        }
        for (ProjectTaskView t : tasks) {
            sb.append("- [").append(t.id()).append("] ").append(t.projectName()).append(": ").append(t.name())
                    .append(" (status ").append(t.status());
            if (t.plannedEnd() != null) {
                sb.append(", due ").append(t.plannedEnd());
            }
            sb.append(")\n");
        }
        sb.append('\n');
    }

    private void appendJournalSection(StringBuilder sb, List<JournalEntryView> entries) {
        sb.append("Recent journal entries:\n");
        if (entries.isEmpty()) {
            sb.append("- (none)\n");
            return;
        }
        for (JournalEntryView e : entries) {
            String content = e.content() == null ? "" : e.content();
            sb.append("- ").append(e.day()).append(": ").append(content).append('\n');
        }
    }
}
