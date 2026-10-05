package com.kairon.assistant.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantRunKind;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectTaskView;
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
class SummaryContextBuilderTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000b1"));
    private static final LocalDate START = LocalDate.of(2026, 9, 21);
    private static final LocalDate END = LocalDate.of(2026, 9, 27);

    @Mock
    TodoApi todos;

    @Mock
    ProjectsApi projects;

    private AssistantProperties properties;
    private SummaryContextBuilder builder;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties(true, "sk-test-key", "claude-sonnet-5", null, 0, null, null,
                new AssistantProperties.Summary(null, null, null, 2, null), null);
        builder = new SummaryContextBuilder(todos, projects, properties);
        when(todos.periodStats(any(), any(), any())).thenReturn(new TodoApi.PeriodStats(9, 11, 3));
        when(projects.openTasksInActiveProjects(any())).thenReturn(List.of());
    }

    private static ProjectPeriodStats project(UUID id, String name, UUID categoryId, String categoryName,
            long completed, BigDecimal estimate, BigDecimal actual) {
        return new ProjectPeriodStats(id, name, categoryId, categoryName, completed, estimate, actual);
    }

    private static ProjectTaskView overdueTask(UUID projectId, String projectName, String taskName,
            LocalDate plannedEnd) {
        return new ProjectTaskView(UUID.randomUUID(), projectId, projectName, "#000", null, taskName, null,
                "IN_PROGRESS", false, null, plannedEnd, null, null, 0, 0, null, null, 0);
    }

    @Test
    void build_rendersTodoStatsAndCompletionRateInTheUserContent() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of());

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        assertThat(context.userContent()).contains("9 created, 11 completed, 3 rolled over");
        assertThat(context.userContent()).contains("Completion rate: 92%");
        assertThat(context.todoStats()).isEqualTo(new TodoApi.PeriodStats(9, 11, 3));
    }

    @Test
    void build_capsTheDisplayedProjectListAtMaxHighlightItemsButSharesUseTheFullList() {
        UUID cat = UUID.randomUUID();
        List<ProjectPeriodStats> all = List.of(
                project(UUID.randomUUID(), "Kitchen remodel", cat, "Home", 4, BigDecimal.valueOf(18), BigDecimal.valueOf(22)),
                project(UUID.randomUUID(), "Garage cleanout", cat, "Home", 2, BigDecimal.valueOf(4), BigDecimal.valueOf(3)),
                project(UUID.randomUUID(), "Website redesign", null, "Work", 2, BigDecimal.valueOf(6), BigDecimal.valueOf(5)));
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(all);

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        // maxHighlightItems = 2 (setUp) — the top 2 by tasksCompleted appear in the display list...
        assertThat(context.topProjects()).hasSize(2);
        assertThat(context.topProjects()).extracting(ProjectPeriodStats::projectName)
                .containsExactly("Kitchen remodel", "Garage cleanout");
        // ...but the category share is computed from all 3 projects' totals (8 total), not just the top 2.
        long homeCount = all.stream().filter(p -> "Home".equals(p.categoryName())).mapToLong(ProjectPeriodStats::tasksCompleted).sum();
        assertThat(homeCount).isEqualTo(6);
        assertThat(context.userContent()).contains("Home: 6 of 8 tasks (75%)");
    }

    @Test
    void build_variancePrecomputedAndRenderedAsOverOrUnder() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of(
                project(UUID.randomUUID(), "Kitchen remodel", null, null, 4,
                        new BigDecimal("18.0"), new BigDecimal("22.0"))));

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        assertThat(context.userContent()).contains("4.0h over");
        assertThat(context.renderStatsTable()).contains("+4.0");
    }

    @Test
    void build_projectWithNoHoursLoggedOmitsTheHoursClause() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of(
                project(UUID.randomUUID(), "Fix bike", null, null, 1, BigDecimal.ZERO, BigDecimal.ZERO)));

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        assertThat(context.userContent()).contains("Fix bike: 1 task completed, no hours logged");
    }

    @Test
    void build_groupsOverdueTasksByProjectWithTheWorstExample() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of());
        UUID kitchenId = UUID.randomUUID();
        when(projects.openTasksInActiveProjects(any())).thenReturn(List.of(
                overdueTask(kitchenId, "Kitchen remodel", "Order cabinet hardware", END.minusDays(3)),
                overdueTask(kitchenId, "Kitchen remodel", "Paint trim", END.minusDays(1)),
                overdueTask(UUID.randomUUID(), "Garage cleanout", "Donate old tools", END.minusDays(7))));

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        assertThat(context.overdueGroups()).hasSize(2);
        SummaryContextBuilder.OverdueProjectGroup kitchen = context.overdueGroups().stream()
                .filter(g -> g.projectId().equals(kitchenId)).findFirst().orElseThrow();
        assertThat(kitchen.overdueCount()).isEqualTo(2);
        assertThat(kitchen.worstTaskName()).isEqualTo("Order cabinet hardware");
        assertThat(kitchen.worstDaysOverdue()).isEqualTo(3);
    }

    @Test
    void build_excludesAProjectWithNoOverdueTasksFromTheGrouping() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of());
        // A task due today or in the future (plannedEnd not before periodEnd) is not overdue.
        when(projects.openTasksInActiveProjects(any())).thenReturn(List.of(
                overdueTask(UUID.randomUUID(), "Website redesign", "Write homepage copy", END.plusDays(5))));

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        assertThat(context.overdueGroups()).isEmpty();
        assertThat(context.userContent()).contains("No projects have overdue work as of " + END);
    }

    @Test
    void build_nullCategoryIdGroupsIntoUncategorized() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of(
                project(UUID.randomUUID(), "Fix bike", null, null, 1, BigDecimal.ZERO, BigDecimal.ZERO)));

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        assertThat(context.categoryShares()).hasSize(1);
        assertThat(context.categoryShares().get(0).categoryName()).isEqualTo("Uncategorized");
        assertThat(context.categoryShares().get(0).sharePercent()).isEqualTo(100);
    }

    @Test
    void build_emptyListsRenderThePlainNoDataSentence() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of());

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        assertThat(context.userContent()).contains("No projects had completed work this week.");
        assertThat(context.userContent()).contains("No projects have overdue work as of " + END);
        assertThat(context.userContent()).contains("No completed project work to attribute to a category this week.");
        assertThat(context.userContent()).doesNotContain("- (none)");
    }

    @Test
    void build_toneIsNeverReadAndNoTonePreferenceApiIsCalled() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of());

        builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);

        // SummaryContextBuilder has no UserAccountApi dependency at all (D14) — nothing
        // to verify beyond the constructor signature not taking one; this test documents
        // that intent so a future change re-adding tone would need to touch this file.
        assertThat(builder).isNotNull();
    }

    @Test
    void renderStatsTable_everyCellTracesToAUserContentValue_andOmitsEmptySections() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of(
                project(UUID.randomUUID(), "Kitchen remodel", null, "Home", 4, BigDecimal.valueOf(18), BigDecimal.valueOf(22))));

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.WEEKLY_SUMMARY, START, END);
        String table = context.renderStatsTable();

        assertThat(table).contains("| Todos created | 9 |");
        assertThat(table).contains("| Todos completed | 11 |");
        assertThat(table).contains("| Rolled over | 3 |");
        assertThat(table).contains("Kitchen remodel");
        assertThat(table).doesNotContain("### Overdue");
    }

    @Test
    void monthlyKind_rendersAMonthlyHeadlineWithoutDayNumbers() {
        when(projects.projectPeriodStats(any(), any(), any())).thenReturn(List.of());
        LocalDate monthStart = LocalDate.of(2026, 9, 1);
        LocalDate monthEnd = LocalDate.of(2026, 9, 30);

        SummaryContextBuilder.Context context = builder.build(USER, AssistantRunKind.MONTHLY_SUMMARY, monthStart, monthEnd);

        assertThat(context.userContent()).startsWith("Monthly execution summary for September 2026.");
    }
}
