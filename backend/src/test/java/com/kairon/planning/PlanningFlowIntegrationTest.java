package com.kairon.planning;

import java.time.LocalDate;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M6 acceptance flow (docs/milestones/M6-today.md §7): a seeded todo, a
 * due project task and no journal entry show up on {@code GET
 * /planning/today}; promoting the task creates a linked todo visible on
 * {@code GET /todo?day=}; promoting a foreign/nonexistent task 404s; adding a
 * journal entry flips {@code hasEntry} on the next {@code GET}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PlanningFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    @Autowired
    MockMvc mvc;

    private String register(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\","
                                + "\"displayName\":\"Flow\",\"timezone\":\"Europe/Amsterdam\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    private String createDueTask(String token, String projectId, String name) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"plannedStart\":\"" + TODAY + "\","
                                + "\"plannedEnd\":\"" + TODAY + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    private String createTodo(String token, String title) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/todo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + TODAY + "\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void todayAggregatesAcrossTodoProjectsAndJournalAndPromoteCreatesALinkedTodo() throws Exception {
        String token = register("planning-flow@example.com");
        String existingTodo = createTodo(token, "Existing todo");
        String project = createProject(token, "Kitchen remodel");
        String dueTask = createDueTask(token, project, "Order cabinets");

        mvc.perform(get("/api/v1/planning/today?date=" + TODAY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todos[0].id").value(existingTodo))
                .andExpect(jsonPath("$.dueProjectTasks[0].id").value(dueTask))
                .andExpect(jsonPath("$.journalPrompt.hasEntry").value(false));

        MvcResult promoted = mvc.perform(post("/api/v1/planning/today:promote")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectTaskId\":\"" + dueTask + "\",\"day\":\"" + TODAY + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Order cabinets"))
                .andExpect(jsonPath("$.sourceProjectTaskId").value(dueTask))
                .andReturn();
        String promotedTodoId = JsonPath.read(promoted.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/todo?day=" + TODAY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + promotedTodoId + "')]").exists());

        // A foreign/nonexistent task 404s and creates no todo (D2/D3).
        mvc.perform(post("/api/v1/planning/today:promote")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectTaskId\":\"" + UUID.randomUUID() + "\",\"day\":\"" + TODAY + "\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/journal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + TODAY + "\",\"content\":\"A quick note.\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/planning/today?date=" + TODAY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journalPrompt.hasEntry").value(true));
    }

    @Test
    void promotingAnotherUsersTaskIs404() throws Exception {
        String ownerToken = register("planning-owner@example.com");
        String project = createProject(ownerToken, "Kitchen remodel");
        String foreignTask = createDueTask(ownerToken, project, "Order cabinets");

        String otherToken = register("planning-other@example.com");
        mvc.perform(post("/api/v1/planning/today:promote")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectTaskId\":\"" + foreignTask + "\",\"day\":\"" + TODAY + "\"}"))
                .andExpect(status().isNotFound());
    }
}
