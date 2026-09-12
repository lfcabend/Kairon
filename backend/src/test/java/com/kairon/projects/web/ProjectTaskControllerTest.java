package com.kairon.projects.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.projects.api.ProjectTaskPage;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.app.ProjectTaskService;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectTaskController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class ProjectTaskControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001101");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProjectTaskService tasks;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static ProjectTaskView view(String name, int position) {
        return new ProjectTaskView(UUID.randomUUID(), UUID.randomUUID(), "Project", "#6366f1", null, name, null,
                "TODO", false, null, null, null, null, 0, position,
                Instant.parse("2026-09-09T08:00:00Z"), Instant.parse("2026-09-09T08:00:00Z"), 0);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    @Test
    void listReturnsThePaginatedEnvelope() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(tasks.list(any(), eq(projectId), any(), any(), any()))
                .thenReturn(new ProjectTaskPage(List.of(view("a", 100), view("b", 200)), 0, 2));

        mvc.perform(get("/api/v1/projects/" + projectId + "/tasks").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("a"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void createReturns201AndTheTask() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(tasks.create(any(), eq(projectId), any())).thenReturn(view("New task", 100));

        mvc.perform(post("/api/v1/projects/" + projectId + "/tasks").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New task\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New task"));
    }

    @Test
    void createWithABlankNameIs400() throws Exception {
        UUID projectId = UUID.randomUUID();
        mvc.perform(post("/api/v1/projects/" + projectId + "/tasks").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithADepthRuleViolationSurfacesThe400FromTheService() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(tasks.create(any(), eq(projectId), any()))
                .thenThrow(ApiException.badRequest("A subtask can't have its own subtasks."));

        mvc.perform(post("/api/v1/projects/" + projectId + "/tasks").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"parentTaskId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchWithAProgressPercentOutOfRangeIs400() throws Exception {
        UUID taskId = UUID.randomUUID();
        mvc.perform(patch("/api/v1/tasks/" + taskId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"status\":\"TODO\",\"progressPercent\":150,\"isMilestone\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchWithAStaleVersionIs409() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(tasks.patch(any(), eq(taskId), any()))
                .thenThrow(ApiException.conflict("This task was modified by another request. Reload and try again."));

        mvc.perform(patch("/api/v1/tasks/" + taskId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"status\":\"TODO\",\"progressPercent\":0,"
                                + "\"isMilestone\":false,\"expectedVersion\":3}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID taskId = UUID.randomUUID();
        mvc.perform(delete("/api/v1/tasks/" + taskId).with(asUser()))
                .andExpect(status().isNoContent());
    }

    @Test
    void reorderMismatchSurfacesThe400FromTheService() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(tasks.reorder(any(), eq(projectId), any(), any()))
                .thenThrow(ApiException.badRequest("`orderedIds` must list exactly that sibling group's current members."));

        mvc.perform(post("/api/v1/projects/" + projectId + "/tasks:reorder").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        UUID projectId = UUID.randomUUID();
        mvc.perform(get("/api/v1/projects/" + projectId + "/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
