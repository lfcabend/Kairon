package com.kairon.projects.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.projects.app.TaskDependencyService;
import com.kairon.projects.app.TaskDependencyView;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskDependencyController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class TaskDependencyControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001201");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TaskDependencyService dependencies;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static TaskDependencyView view(UUID predecessorId, UUID successorId, boolean violatesConstraint) {
        return new TaskDependencyView(UUID.randomUUID(), predecessorId, successorId, "FS", 0, violatesConstraint,
                Instant.parse("2026-09-10T08:00:00Z"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    @Test
    void listReturnsTheEdgesWithViolatesConstraint() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(dependencies.list(any(), eq(projectId))).thenReturn(List.of(view(a, b, true)));

        mvc.perform(get("/api/v1/projects/" + projectId + "/dependencies").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predecessorId").value(a.toString()))
                .andExpect(jsonPath("$[0].violatesConstraint").value(true));
    }

    @Test
    void createReturns201AndTheEdge() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        when(dependencies.create(any(), eq(taskId), any())).thenReturn(view(predecessorId, taskId, false));

        mvc.perform(post("/api/v1/tasks/" + taskId + "/dependencies").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorId\":\"" + predecessorId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.predecessorId").value(predecessorId.toString()));
    }

    @Test
    void createWithoutAPredecessorIs400() throws Exception {
        UUID taskId = UUID.randomUUID();
        mvc.perform(post("/api/v1/tasks/" + taskId + "/dependencies").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithACycleSurfacesThe400FromTheService() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(dependencies.create(any(), eq(taskId), any()))
                .thenThrow(ApiException.badRequest("This would create a circular dependency."));

        mvc.perform(post("/api/v1/tasks/" + taskId + "/dependencies").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithADuplicateSurfacesThe409FromTheService() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(dependencies.create(any(), eq(taskId), any()))
                .thenThrow(ApiException.conflict("This dependency already exists."));

        mvc.perform(post("/api/v1/tasks/" + taskId + "/dependencies").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID depId = UUID.randomUUID();
        mvc.perform(delete("/api/v1/dependencies/" + depId).with(asUser()))
                .andExpect(status().isNoContent());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        UUID projectId = UUID.randomUUID();
        mvc.perform(get("/api/v1/projects/" + projectId + "/dependencies"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
