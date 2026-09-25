package com.kairon.assistant.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.kairon.assistant.domain.AssistantSuggestedProject;
import com.kairon.assistant.llm.PlannedDependencyPayload;
import com.kairon.assistant.llm.PlannedTaskPayload;
import com.kairon.assistant.repo.AssistantSuggestedProjectRepository;
import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectsApi.ProjectPlanCommand;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestedProjectServiceTest {

    private static final UserId USER = UserId.of(UUID.fromString("018f5b3e-0000-7000-8000-0000000000f1"));
    private static final UUID RUN_ID = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Mock
    AssistantSuggestedProjectRepository suggestedProjects;

    @Mock
    ProjectsApi projectsApi;

    @Mock
    ObjectMapper objectMapper;

    SuggestedProjectService service;

    private PersistedProjectPlan plan;

    @BeforeEach
    void setUp() {
        service = new SuggestedProjectService(suggestedProjects, projectsApi, objectMapper);
        plan = new PersistedProjectPlan("Kitchen remodel", "desc", "M", DAY, DAY.plusDays(30),
                List.of(
                        new PlannedTaskPayload("t1", null, "Design", null, false, DAY, DAY.plusDays(6), null),
                        new PlannedTaskPayload("t2", "t1", "Pick materials", null, false, DAY, DAY.plusDays(2),
                                null)),
                List.of(new PlannedDependencyPayload("t1", "t2", "FS", 0)));
        org.mockito.Mockito.lenient().when(objectMapper.convertValue(any(), eq(PersistedProjectPlan.class)))
                .thenAnswer(inv -> plan);
    }

    private AssistantSuggestedProject proposed() {
        return AssistantSuggestedProject.propose(RUN_ID, USER.value(), Map.of());
    }

    @Test
    void acceptBuildsACommandFromTheStoredPlanAndCreatesTheProject() {
        AssistantSuggestedProject row = proposed();
        when(suggestedProjects.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));
        ProjectView created = projectView();
        when(projectsApi.createFromPlan(eq(USER), any())).thenReturn(created);

        ProjectView result = service.accept(USER, row.getId(), List.of());

        assertThat(result).isEqualTo(created);
        assertThat(row.getStatus().name()).isEqualTo("ACCEPTED");
        assertThat(row.getAcceptedProjectId()).isEqualTo(created.id());
        ArgumentCaptor<ProjectPlanCommand> captor = ArgumentCaptor.forClass(ProjectPlanCommand.class);
        verify(projectsApi).createFromPlan(eq(USER), captor.capture());
        assertThat(captor.getValue().tasks()).hasSize(2);
        assertThat(captor.getValue().dependencies()).hasSize(1);
    }

    @Test
    void acceptExcludingAParentCascadesToItsChildren() {
        AssistantSuggestedProject row = proposed();
        when(suggestedProjects.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));
        when(projectsApi.createFromPlan(eq(USER), any())).thenReturn(projectView());

        service.accept(USER, row.getId(), List.of("t1"));

        ArgumentCaptor<ProjectPlanCommand> captor = ArgumentCaptor.forClass(ProjectPlanCommand.class);
        verify(projectsApi).createFromPlan(eq(USER), captor.capture());
        // t1 excluded -> t2 (its child) cascades out too -> no tasks, no dependency left.
        assertThat(captor.getValue().tasks()).isEmpty();
        assertThat(captor.getValue().dependencies()).isEmpty();
    }

    @Test
    void acceptOnAnAlreadyResolvedPlanIsAConflict() {
        AssistantSuggestedProject row = proposed();
        row.dismiss();
        when(suggestedProjects.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.accept(USER, row.getId(), List.of()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(409));
        verify(projectsApi, never()).createFromPlan(any(), any());
    }

    @Test
    void dismissFlipsStatusWithoutTouchingProjectsApi() {
        AssistantSuggestedProject row = proposed();
        when(suggestedProjects.findByIdAndUserId(row.getId(), USER.value())).thenReturn(Optional.of(row));

        AssistantSuggestedProjectView view = service.dismiss(USER, row.getId());

        assertThat(view.status()).isEqualTo("DISMISSED");
        verify(projectsApi, never()).createFromPlan(any(), any());
    }

    @Test
    void missingOrForeignPlanIs404() {
        UUID id = UUID.randomUUID();
        when(suggestedProjects.findByIdAndUserId(id, USER.value())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismiss(USER, id))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    private static ProjectView projectView() {
        UUID id = UUID.randomUUID();
        return new ProjectView(id, null, "Kitchen remodel", null, "PLANNING", null, 100, "#6366f1",
                DAY, DAY.plusDays(30), null, null, Instant.now(), Instant.now(), 0);
    }
}
