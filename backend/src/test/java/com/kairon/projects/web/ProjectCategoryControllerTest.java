package com.kairon.projects.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;
import com.kairon.projects.app.ProjectCategoryService;
import com.kairon.projects.app.ProjectCategoryView;

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

@WebMvcTest(ProjectCategoryController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class ProjectCategoryControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000000f01");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProjectCategoryService categories;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static ProjectCategoryView view(String name, int position) {
        return new ProjectCategoryView(UUID.randomUUID(), name, "#6366f1", position,
                Instant.parse("2026-09-09T08:00:00Z"), Instant.parse("2026-09-09T08:00:00Z"), 0);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    @Test
    void listReturnsABareArrayInPositionOrder() throws Exception {
        when(categories.list(any())).thenReturn(List.of(view("Home", 100), view("Work", 200)));

        mvc.perform(get("/api/v1/project-categories").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Home"));
    }

    @Test
    void createReturns201AndTheCategory() throws Exception {
        when(categories.create(any(), any())).thenReturn(view("Home", 100));

        mvc.perform(post("/api/v1/project-categories").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Home"));
    }

    @Test
    void createWithABlankNameIs400() throws Exception {
        mvc.perform(post("/api/v1/project-categories").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithAnInvalidColorIs400() throws Exception {
        mvc.perform(post("/api/v1/project-categories").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home\",\"color\":\"blue\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDuplicateNameSurfacesThe409FromTheService() throws Exception {
        when(categories.create(any(), any()))
                .thenThrow(ApiException.conflict("A category named \"Home\" already exists."));

        mvc.perform(post("/api/v1/project-categories").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void patchWithAStaleVersionIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(categories.patch(any(), eq(id), any()))
                .thenThrow(ApiException.conflict("This category was modified by another request. Reload and try again."));

        mvc.perform(patch("/api/v1/project-categories/" + id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home\",\"expectedVersion\":3}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/project-categories/" + id).with(asUser()))
                .andExpect(status().isNoContent());
    }

    @Test
    void reorderMismatchSurfacesThe400FromTheService() throws Exception {
        when(categories.reorder(any(), any()))
                .thenThrow(ApiException.badRequest("`orderedIds` must list exactly the user's current categories."));

        mvc.perform(post("/api/v1/project-categories:reorder").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/project-categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
