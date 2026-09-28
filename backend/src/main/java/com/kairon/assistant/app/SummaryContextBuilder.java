package com.kairon.assistant.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.todo.api.TodoApi;

import org.springframework.stereotype.Component;

/**
 * Builds the system prompt, user content, and (via {@link Context#renderStatsTable()})
 * the deterministic stats table for a {@code WEEKLY_SUMMARY}/{@code MONTHLY_SUMMARY}
 * run. No journal, no tone (M9 D14) — five data sources over {@code todo}/
 * {@code projects} only (docs/milestones/M9-execution-summaries.md §4.5). One
 * {@code kind}-independent system prompt, unlike {@link TodoSuggestionContextBuilder}'s
 * two {@code Horizon} variants — nothing here structurally differs between a
 * week and a month.
 */
@Component
class SummaryContextBuilder {

    // Kept in sync with docs/milestones/M9-execution-summaries.md §4.5 — that
    // doc is the source of truth for *why* this prompt is shaped this way.
    private static final String SYSTEM_PROMPT = """
            You are Kairon's execution-summary assistant. Write a short, honest
            narrative about how the stated period of work went, based only on the
            numbers and item lists in the user message below. Never invent a task,
            project, or number that isn't given to you, and never recompute or
            second-guess a number you're handed — treat every count and every hours
            figure as already correct.

            Structure your response as markdown with these sections:
            - A one-line headline verdict.
            - "What got done" — reference the completion rate, briefly the busiest
              project(s) by tasks completed, and which category or categories got the
              most attention this period using the given share percentages (e.g. "most
              of this week's work was in the Home category"). If one category's share
              is much larger than the others, say so plainly — that imbalance is itself
              worth naming, not just the raw numbers.
            - "Where things slipped" — name the specific **projects** carrying overdue
              work (their overdue count and the named worst example given to you) and
              any project whose actual hours ran notably over estimate. Flag risk at
              the project level, not as a list of individual late task titles. If both
              lists are empty or minor, say plainly that nothing slipped — don't invent
              a problem to fill this section.
            - "Suggestions" — 1 to 3 concrete, specific observations grounded in the
              numbers above (never generic productivity advice unconnected to this
              period's actual data). A category that's been getting little or no
              attention while carrying overdue projects is exactly the kind of thing
              worth calling out here.

            Keep the whole response under about 300 words. Plain, direct language — you
            are describing what already happened, not motivating the user to do
            something next.""";

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final TodoApi todos;
    private final ProjectsApi projects;
    private final AssistantProperties properties;

    SummaryContextBuilder(TodoApi todos, ProjectsApi projects, AssistantProperties properties) {
        this.todos = todos;
        this.projects = projects;
        this.properties = properties;
    }

    record OverdueProjectGroup(
            UUID projectId, String projectName, long overdueCount, String worstTaskName,
            LocalDate worstPlannedEnd, long worstDaysOverdue) {
    }

    record CategoryShare(UUID categoryId, String categoryName, long tasksCompleted, int sharePercent) {
    }

    record Context(
            String systemPrompt, String userContent, Map<String, Object> inputSnapshot,
            TodoApi.PeriodStats todoStats,
            List<ProjectsApi.ProjectPeriodStats> topProjects,
            List<OverdueProjectGroup> overdueGroups,
            List<CategoryShare> categoryShares) {

        /**
         * A pure function over the same structured values already rendered into
         * {@link #userContent} — never (re)computes, only formats — so the table and
         * the narrative are guaranteed to describe the exact same snapshot (M9 D16).
         */
        String renderStatsTable() {
            StringBuilder sb = new StringBuilder();
            sb.append("## Stats\n\n");
            sb.append("| Metric | Value |\n| --- | --- |\n");
            sb.append("| Todos created | ").append(todoStats.created()).append(" |\n");
            sb.append("| Todos completed | ").append(todoStats.completed()).append(" |\n");
            sb.append("| Rolled over | ").append(todoStats.rolledOver()).append(" |\n");
            sb.append("| Completion rate | ").append(Math.round(todoStats.completionRate() * 100)).append("% |\n");

            if (!topProjects.isEmpty()) {
                sb.append("\n### By project\n\n");
                sb.append("| Project | Category | Completed | Est. hours | Actual hours | Variance |\n");
                sb.append("| --- | --- | --- | --- | --- | --- |\n");
                for (ProjectsApi.ProjectPeriodStats p : topProjects) {
                    boolean noHours = p.estimateHoursCompleted().signum() == 0 && p.actualHoursCompleted().signum() == 0;
                    sb.append("| ").append(p.projectName()).append(" | ")
                            .append(p.categoryName() == null ? "Uncategorized" : p.categoryName()).append(" | ")
                            .append(p.tasksCompleted()).append(" | ")
                            .append(noHours ? "—" : p.estimateHoursCompleted().toPlainString()).append(" | ")
                            .append(noHours ? "—" : p.actualHoursCompleted().toPlainString()).append(" | ")
                            .append(noHours ? "—" : formatVariance(p.actualHoursCompleted().subtract(p.estimateHoursCompleted())))
                            .append(" |\n");
                }
            }

            if (!overdueGroups.isEmpty()) {
                sb.append("\n### Overdue\n\n");
                sb.append("| Project | Overdue tasks | Worst example | Days overdue |\n");
                sb.append("| --- | --- | --- | --- |\n");
                for (OverdueProjectGroup g : overdueGroups) {
                    sb.append("| ").append(g.projectName()).append(" | ").append(g.overdueCount()).append(" | ")
                            .append(g.worstTaskName()).append(" | ").append(g.worstDaysOverdue()).append(" |\n");
                }
            }

            if (!categoryShares.isEmpty()) {
                sb.append("\n### By category\n\n");
                sb.append("| Category | Completed tasks | Share |\n");
                sb.append("| --- | --- | --- |\n");
                for (CategoryShare c : categoryShares) {
                    sb.append("| ").append(c.categoryName()).append(" | ").append(c.tasksCompleted()).append(" | ")
                            .append(c.sharePercent()).append("% |\n");
                }
            }
            return sb.toString().stripTrailing();
        }

        private static String formatVariance(BigDecimal variance) {
            return variance.signum() > 0 ? "+" + variance.toPlainString() : variance.toPlainString();
        }
    }

    Context build(UserId userId, AssistantRunKind kind, LocalDate periodStart, LocalDate periodEnd) {
        int maxItems = properties.summary().maxHighlightItems();
        boolean isWeek = kind == AssistantRunKind.WEEKLY_SUMMARY;

        TodoApi.PeriodStats todoStats = todos.periodStats(userId, periodStart, periodEnd);

        List<ProjectsApi.ProjectPeriodStats> allProjectStats = projects.projectPeriodStats(userId, periodStart, periodEnd);
        List<ProjectsApi.ProjectPeriodStats> topProjects = allProjectStats.stream()
                .sorted(Comparator.comparingLong(ProjectsApi.ProjectPeriodStats::tasksCompleted).reversed())
                .limit(maxItems)
                .toList();

        List<ProjectTaskView> overdueTasks = projects.openTasksInActiveProjects(userId).stream()
                .filter(t -> t.plannedEnd() != null && t.plannedEnd().isBefore(periodEnd))
                .toList();
        List<OverdueProjectGroup> overdueGroups = groupOverdue(overdueTasks, periodEnd, maxItems);

        long totalCompleted = allProjectStats.stream().mapToLong(ProjectsApi.ProjectPeriodStats::tasksCompleted).sum();
        List<CategoryShare> categoryShares = computeCategoryShares(allProjectStats, totalCompleted, maxItems);

        String periodLabel = periodLabel(isWeek, periodStart, periodEnd);
        String userContent = renderUserContent(isWeek, periodLabel, periodEnd, todoStats, topProjects,
                overdueGroups, categoryShares, totalCompleted);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("systemPrompt", SYSTEM_PROMPT);
        snapshot.put("userContent", userContent);
        return new Context(SYSTEM_PROMPT, userContent, snapshot, todoStats, topProjects, overdueGroups, categoryShares);
    }

    private List<OverdueProjectGroup> groupOverdue(List<ProjectTaskView> overdueTasks, LocalDate periodEnd, int maxItems) {
        Map<UUID, List<ProjectTaskView>> byProject = overdueTasks.stream()
                .collect(Collectors.groupingBy(ProjectTaskView::projectId, LinkedHashMap::new, Collectors.toList()));
        List<OverdueProjectGroup> groups = new ArrayList<>();
        for (Map.Entry<UUID, List<ProjectTaskView>> entry : byProject.entrySet()) {
            List<ProjectTaskView> tasksForProject = entry.getValue();
            ProjectTaskView worst = tasksForProject.stream()
                    .max(Comparator.comparingLong(t -> ChronoUnit.DAYS.between(t.plannedEnd(), periodEnd)))
                    .orElseThrow();
            long worstDays = ChronoUnit.DAYS.between(worst.plannedEnd(), periodEnd);
            groups.add(new OverdueProjectGroup(entry.getKey(), worst.projectName(), tasksForProject.size(),
                    worst.name(), worst.plannedEnd(), worstDays));
        }
        return groups.stream()
                .sorted(Comparator.comparingLong(OverdueProjectGroup::overdueCount).reversed()
                        .thenComparing(Comparator.comparingLong(OverdueProjectGroup::worstDaysOverdue).reversed()))
                .limit(maxItems)
                .toList();
    }

    private List<CategoryShare> computeCategoryShares(List<ProjectsApi.ProjectPeriodStats> allProjectStats,
            long totalCompleted, int maxItems) {
        Map<UUID, String> namesById = new LinkedHashMap<>();
        Map<UUID, Long> countsById = new LinkedHashMap<>();
        for (ProjectsApi.ProjectPeriodStats p : allProjectStats) {
            namesById.putIfAbsent(p.categoryId(), p.categoryId() == null ? "Uncategorized" : p.categoryName());
            countsById.merge(p.categoryId(), p.tasksCompleted(), Long::sum);
        }
        return countsById.entrySet().stream()
                .map(e -> new CategoryShare(e.getKey(), namesById.get(e.getKey()), e.getValue(),
                        totalCompleted == 0 ? 0 : (int) Math.round(100.0 * e.getValue() / totalCompleted)))
                .sorted(Comparator.comparingLong(CategoryShare::tasksCompleted).reversed())
                .limit(maxItems)
                .toList();
    }

    private String periodLabel(boolean isWeek, LocalDate start, LocalDate end) {
        if (!isWeek) {
            return start.format(MONTH_YEAR);
        }
        if (start.getYear() == end.getYear()) {
            return start.format(MONTH_DAY) + "–" + end.format(MONTH_DAY_YEAR);
        }
        return start.format(MONTH_DAY_YEAR) + "–" + end.format(MONTH_DAY_YEAR);
    }

    private String renderUserContent(boolean isWeek, String periodLabel, LocalDate periodEnd,
            TodoApi.PeriodStats todoStats, List<ProjectsApi.ProjectPeriodStats> topProjects,
            List<OverdueProjectGroup> overdueGroups, List<CategoryShare> categoryShares, long totalCompleted) {
        String periodNoun = isWeek ? "week" : "month";
        StringBuilder sb = new StringBuilder();
        sb.append(isWeek ? "Weekly execution summary for " : "Monthly execution summary for ")
                .append(periodLabel).append(".\n\n");

        long onDocket = todoStats.created() + todoStats.rolledOver();
        sb.append("Todos: ").append(todoStats.created()).append(" created, ")
                .append(todoStats.completed()).append(" completed, ")
                .append(todoStats.rolledOver()).append(" rolled over from a previous day.\n");
        sb.append("Completion rate: ").append(Math.round(todoStats.completionRate() * 100)).append("% (")
                .append(todoStats.completed()).append(" of ").append(onDocket)
                .append(" items on the docket this ").append(periodNoun).append(").\n\n");

        if (topProjects.isEmpty()) {
            sb.append("No projects had completed work this ").append(periodNoun).append(".\n\n");
        } else {
            sb.append("Projects with completed work this ").append(periodNoun).append(":\n");
            for (ProjectsApi.ProjectPeriodStats p : topProjects) {
                sb.append("- ").append(p.projectName());
                if (p.categoryName() != null) {
                    sb.append(" (").append(p.categoryName()).append(")");
                }
                sb.append(": ").append(p.tasksCompleted()).append(plural(p.tasksCompleted(), " task", " tasks"))
                        .append(" completed");
                boolean noHours = p.estimateHoursCompleted().signum() == 0 && p.actualHoursCompleted().signum() == 0;
                if (noHours) {
                    sb.append(", no hours logged");
                } else {
                    BigDecimal variance = p.actualHoursCompleted().subtract(p.estimateHoursCompleted());
                    sb.append(", ").append(p.estimateHoursCompleted().toPlainString()).append("h estimated, ")
                            .append(p.actualHoursCompleted().toPlainString()).append("h actual");
                    if (variance.signum() != 0) {
                        sb.append(" (").append(variance.abs().toPlainString()).append("h ")
                                .append(variance.signum() > 0 ? "over" : "under").append(")");
                    }
                }
                sb.append(".\n");
            }
            sb.append('\n');
        }

        if (overdueGroups.isEmpty()) {
            sb.append("No projects have overdue work as of ").append(periodEnd).append(".\n\n");
        } else {
            sb.append("Projects with overdue work as of ").append(periodEnd).append(":\n");
            for (OverdueProjectGroup g : overdueGroups) {
                sb.append("- ").append(g.projectName()).append(": ").append(g.overdueCount())
                        .append(plural(g.overdueCount(), " task", " tasks")).append(" overdue (worst: \"")
                        .append(g.worstTaskName()).append("\", planned end ").append(g.worstPlannedEnd())
                        .append(", ").append(g.worstDaysOverdue())
                        .append(plural(g.worstDaysOverdue(), " day", " days")).append(" overdue).\n");
            }
            sb.append('\n');
        }

        if (categoryShares.isEmpty()) {
            sb.append("No completed project work to attribute to a category this ").append(periodNoun).append(".\n");
        } else {
            sb.append("Categories by share of completed project work this ").append(periodNoun).append(":\n");
            for (CategoryShare c : categoryShares) {
                sb.append("- ").append(c.categoryName()).append(": ").append(c.tasksCompleted()).append(" of ")
                        .append(totalCompleted).append(" tasks (").append(c.sharePercent()).append("%).\n");
            }
        }
        return sb.toString();
    }

    private static String plural(long count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }
}
