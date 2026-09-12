package com.kairon.projects.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.projects.api.ProjectPage;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.app.ProjectService;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class ProjectControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001001");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProjectService projects;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static ProjectView view(String name, int priorityRank) {
        return new ProjectView(UUID.randomUUID(), null, name, null, "PLANNING", null, priorityRank, "#6366f1",
                null, null, null, null, Instant.parse("2026-09-09T08:00:00Z"),
                Instant.parse("2026-09-09T08:00:00Z"), 0);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    @Test
    void listReturnsThePaginatedEnvelope() throws Exception {
        when(projects.list(any(), eq((String) null), eq((UUID) null), eq((String) null), eq(false), anyInt(),
                anyInt(), any()))
                .thenReturn(new ProjectPage(List.of(view("Alpha", 100)), 0, 1));

        mvc.perform(get("/api/v1/projects").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alpha"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listBindsPageSizeAndSortWithoutCollidingWithTheSizeFilter() throws Exception {
        when(projects.list(any(), any(), any(), eq("M"), anyBoolean(), eq(0), eq(5), any()))
                .thenReturn(new ProjectPage(List.of(), 0, 0));

        mvc.perform(get("/api/v1/projects").param("size", "M").param("pageSize", "5")
                        .param("sort", "name,asc").with(asUser()))
                .andExpect(status().isOk());
    }

    @Test
    void priorityOrderedReturnsABareArray() throws Exception {
        when(projects.listByPriority(any())).thenReturn(List.of(view("Alpha", 100), view("Beta", 200)));

        mvc.perform(get("/api/v1/projects/priority-ordered").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Alpha"));
    }

    @Test
    void createReturns201AndTheProject() throws Exception {
        when(projects.create(any(), any())).thenReturn(view("New project", 100));

        mvc.perform(post("/api/v1/projects").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New project\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New project"));
    }

    @Test
    void createWithABlankNameIs400() throws Exception {
        mvc.perform(post("/api/v1/projects").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithAForeignCategorySurfacesThe404FromTheService() throws Exception {
        when(projects.create(any(), any())).thenThrow(ApiException.notFound("Project category not found."));

        mvc.perform(post("/api/v1/projects").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"categoryId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchIgnoresAPriorityRankFieldSmuggledIntoTheBody() throws Exception {
        when(projects.patch(any(), any(), any())).thenReturn(view("Renamed", 999));

        mvc.perform(patch("/api/v1/projects/" + UUID.randomUUID()).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"status\":\"ACTIVE\",\"priorityRank\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityRank").value(999));
    }

    @Test
    void patchWithAStaleVersionIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(projects.patch(any(), eq(id), any()))
                .thenThrow(ApiException.conflict("This project was modified by another request. Reload and try again."));

        mvc.perform(patch("/api/v1/projects/" + id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"status\":\"ACTIVE\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reorderMismatchSurfacesThe400FromTheService() throws Exception {
        when(projects.reorder(any(), any())).thenThrow(
                ApiException.badRequest("`orderedIds` must list exactly the user's current non-archived projects."));

        mvc.perform(post("/api/v1/projects:reorder").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
