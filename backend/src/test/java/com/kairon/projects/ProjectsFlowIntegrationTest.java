package com.kairon.projects;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.kairon.projects.repo.ProjectTaskRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M4 acceptance flow (docs/milestones/M4-projects-core.md §7): register ->
 * a category -> two projects in it -> reorder -> a task tree -> reorder ->
 * reparent -> delete a project (cascades to its tasks) -> delete the category
 * (the remaining project becomes uncategorized), against a real PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProjectsFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ProjectTaskRepository taskRepo;

    private String register(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\","
                                + "\"displayName\":\"Flow\",\"timezone\":\"Europe/Amsterdam\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createCategory(String token, String name) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/project-categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    private String createProject(String token, String name, String categoryId) throws Exception {
        String body = categoryId == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"categoryId\":\"" + categoryId + "\"}";
        MvcResult res = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    private String createTask(String token, String projectId, String name, String parentTaskId) throws Exception {
        String body = parentTaskId == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"parentTaskId\":\"" + parentTaskId + "\"}";
        MvcResult res = mvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void categoriesProjectsAndTasksEndToEnd() throws Exception {
        String token = register("projects-flow@example.com");
        String category = createCategory(token, "Home");

        String projectA = createProject(token, "Alpha", category);
        String projectB = createProject(token, "Beta", category);

        mvc.perform(post("/api/v1/projects:reorder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[\"" + projectB + "\",\"" + projectA + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectB))
                .andExpect(jsonPath("$[0].priorityRank").value(100))
                .andExpect(jsonPath("$[1].priorityRank").value(200));

        String top = createTask(token, projectA, "Top task", null);
        String sibling = createTask(token, projectA, "Sibling task", null);
        String child = createTask(token, projectA, "Child task", top);

        mvc.perform(post("/api/v1/projects/" + projectA + "/tasks:reorder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[\"" + sibling + "\",\"" + top + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sibling))
                .andExpect(jsonPath("$[0].position").value(100));

        // Reparent the child under the sibling instead.
        mvc.perform(patch("/api/v1/tasks/" + child)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Child task\",\"status\":\"TODO\",\"parentTaskId\":\"" + sibling + "\","
                                + "\"progressPercent\":0,\"isMilestone\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentTaskId").value(sibling));

        // Deleting the project cascades a soft-delete to all of its tasks (D8).
        mvc.perform(delete("/api/v1/projects/" + projectA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // The project itself is gone (soft-deleted, same as any other resolved-by-userId row)...
        mvc.perform(get("/api/v1/projects/" + projectA + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        // ...and D8's cascade soft-deleted every one of its tasks too.
        org.assertj.core.api.Assertions.assertThat(taskRepo.findByProjectIdAndDeletedAtIsNull(UUID.fromString(projectA)))
                .isEmpty();

        // Deleting the category is a hard delete; the remaining project becomes uncategorized (D14).
        mvc.perform(delete("/api/v1/project-categories/" + category)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/projects/" + projectB)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(org.hamcrest.Matchers.nullValue()));
    }

    /**
     * M5's addition (docs/milestones/M5-gantt-dependencies.md §7): an FS edge
     * between two tasks, the reverse edge rejected as a cycle, then deleting the
     * predecessor task hard-deletes the edge too (D5).
     */
    @Test
    void taskDependenciesEndToEnd() throws Exception {
        String token = register("projects-deps-flow@example.com");
        String project = createProject(token, "Gamma", null);
        String predecessor = createTask(token, project, "Design", null);
        String successor = createTask(token, project, "Build", null);

        MvcResult created = mvc.perform(post("/api/v1/tasks/" + successor + "/dependencies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorId\":\"" + predecessor + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String dependencyId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/projects/" + project + "/dependencies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].predecessorId").value(predecessor))
                .andExpect(jsonPath("$[0].successorId").value(successor));

        // The reverse edge would close a cycle (D3).
        mvc.perform(post("/api/v1/tasks/" + predecessor + "/dependencies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorId\":\"" + successor + "\"}"))
                .andExpect(status().isBadRequest());

        // Deleting the predecessor task hard-deletes the edge (D5) — no dangling reference remains.
        mvc.perform(delete("/api/v1/tasks/" + predecessor)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/projects/" + project + "/dependencies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // The dependency id itself is gone too, not just absent from the list.
        mvc.perform(delete("/api/v1/dependencies/" + dependencyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
