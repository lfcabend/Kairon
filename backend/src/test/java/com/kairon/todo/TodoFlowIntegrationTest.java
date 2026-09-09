package com.kairon.todo;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M2 acceptance flow (docs/milestones/M2 §7): register → create three items →
 * reorder → complete one → roll the rest onto the next day, against a real
 * PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class TodoFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    private String register() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"todo-flow@example.com","password":"correct horse battery",
                                 "displayName":"Flow","timezone":"Europe/Amsterdam"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private String create(String token, String day, String title) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/todo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + day + "\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createReorderCompleteThenRollForward() throws Exception {
        String token = register();
        String day = "2026-09-09";
        String next = "2026-09-10";

        String a = create(token, day, "alpha");
        String b = create(token, day, "beta");
        String c = create(token, day, "gamma");

        mvc.perform(get("/api/v1/todo").param("day", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("alpha"));

        mvc.perform(post("/api/v1/todo:reorder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + day + "\",\"orderedIds\":[\"" + c + "\",\"" + b + "\",\"" + a + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("gamma"))
                .andExpect(jsonPath("$[0].position").value(100));

        mvc.perform(post("/api/v1/todo/" + a + ":complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        mvc.perform(post("/api/v1/todo:rollover")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toDay\":\"" + next + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolledOver", hasSize(2)));

        // Next day carries the two unfinished items, each linked back to its source.
        mvc.perform(get("/api/v1/todo").param("day", next).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title",
                        org.hamcrest.Matchers.containsInAnyOrder("beta", "gamma")))
                .andExpect(jsonPath("$[0].rolledOverFromId").isNotEmpty());

        // Source day: the completed item stays DONE, the carried ones are CANCELLED.
        mvc.perform(get("/api/v1/todo").param("day", day).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'alpha')].status").value("DONE"))
                .andExpect(jsonPath("$[?(@.title == 'beta')].status").value("CANCELLED"))
                .andExpect(jsonPath("$[?(@.title == 'gamma')].status").value("CANCELLED"));
    }
}
