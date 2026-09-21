package com.kairon.planning.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.planning.app.PlanningService;
import com.kairon.planning.app.PlanningService.TodayView;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.todo.api.TodoItemView;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlanningController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class PlanningControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001201");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlanningService planning;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    private static TodoItemView todo(UUID id) {
        return new TodoItemView(id, DAY, "A todo", null, "OPEN", 0, 100, null, null, null, null,
                Instant.parse("2026-09-18T08:00:00Z"), Instant.parse("2026-09-18T08:00:00Z"), 0);
    }

    private static ProjectTaskView task(UUID id, String name) {
        return new ProjectTaskView(id, UUID.randomUUID(), "Project", "#6366f1", null, name, null,
                "TODO", false, null, null, null, null, 0, 100,
                Instant.parse("2026-09-18T08:00:00Z"), Instant.parse("2026-09-18T08:00:00Z"), 0);
    }

    @Test
    void todayReturnsTheAssembledView() throws Exception {
        UUID todoId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(planning.today(any(), eq(DAY))).thenReturn(
                new TodayView(DAY, List.of(todo(todoId)), List.of(task(taskId, "Order cabinets")), true));

        mvc.perform(get("/api/v1/planning/today?date=" + DAY).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DAY.toString()))
                .andExpect(jsonPath("$.todos[0].id").value(todoId.toString()))
                .andExpect(jsonPath("$.dueProjectTasks[0].name").value("Order cabinets"))
                .andExpect(jsonPath("$.journalPrompt.hasEntry").value(true));
    }

    @Test
    void todayWithoutADateIs400() throws Exception {
        mvc.perform(get("/api/v1/planning/today").with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void promoteReturns201AndTheCreatedTodo() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID createdId = UUID.randomUUID();
        when(planning.promote(any(), eq(taskId), eq(DAY))).thenReturn(todo(createdId));

        mvc.perform(post("/api/v1/planning/today:promote").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectTaskId\":\"" + taskId + "\",\"day\":\"" + DAY + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(createdId.toString()));
    }

    @Test
    void promoteWithAForeignOrMissingTaskSurfacesThe404FromTheService() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(planning.promote(any(), eq(taskId), eq(DAY)))
                .thenThrow(ApiException.notFound("Task not found."));

        mvc.perform(post("/api/v1/planning/today:promote").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectTaskId\":\"" + taskId + "\",\"day\":\"" + DAY + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/planning/today?date=" + DAY))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
