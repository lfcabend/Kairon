package com.kairon.planning;

import java.time.LocalDate;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M11 acceptance flow for {@code GET /sync}
 * (docs/milestones/M11-android-foundation.md §7): two successive calls — the
 * first with no {@code since} (a full bootstrap), the second with the first
 * call's own returned {@code since} — together account for every row exactly
 * once, including one row deleted in between.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SyncFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @Autowired
    MockMvc mvc;

    private String register(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\","
                                + "\"displayName\":\"Flow\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createTodo(String token, String title) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/todo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void twoSuccessiveSyncCallsTogetherAccountForEveryRowExactlyOnce() throws Exception {
        String token = register("sync-flow@example.com");
        String todoA = createTodo(token, "First todo");

        // --- bootstrap: no `since` -------------------------------------------------
        MvcResult first = mvc.perform(get("/api/v1/sync?types=todo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todos.upserted[?(@.id=='" + todoA + "')]").exists())
                .andExpect(jsonPath("$.todos.deletedIds").isEmpty())
                .andExpect(jsonPath("$.todos.truncated").value(false))
                .andReturn();
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.since");

        // --- a second row is created, and the first is soft-deleted, after the cursor --
        String todoB = createTodo(token, "Second todo");
        mvc.perform(delete("/api/v1/todo/" + todoA).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // --- delta call: only the change since the cursor ---------------------------
        mvc.perform(get("/api/v1/sync?types=todo&since=" + cursor)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todos.upserted[?(@.id=='" + todoB + "')]").exists())
                .andExpect(jsonPath("$.todos.upserted[?(@.id=='" + todoA + "')]").doesNotExist())
                .andExpect(jsonPath("$.todos.deletedIds[0]").value(todoA))
                .andExpect(jsonPath("$.todos.truncated").value(false));

        // --- a brand-new user's bootstrap is empty, not an error ---------------------
        String otherToken = register("sync-flow-other@example.com");
        mvc.perform(get("/api/v1/sync").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todos.upserted").isEmpty())
                .andExpect(jsonPath("$.journalEntries.upserted").isEmpty())
                .andExpect(jsonPath("$.projects.upserted").isEmpty())
                .andExpect(jsonPath("$.projectTasks.upserted").isEmpty());
    }

    @Test
    void anUnknownSyncTypeIs400() throws Exception {
        String token = register("sync-flow-bad-type@example.com");

        mvc.perform(get("/api/v1/sync?types=bogus").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(get("/api/v1/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }
}
