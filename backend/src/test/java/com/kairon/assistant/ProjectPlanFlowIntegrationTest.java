package com.kairon.assistant;

import java.time.LocalDate;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.ProjectPlanResult;
import com.kairon.assistant.llm.PlannedDependencyPayload;
import com.kairon.assistant.llm.PlannedTaskPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M8.5 acceptance flow (docs/milestones/M8.5-project-generation.md): a
 * user not opted in gets 403; opting in and generating a plan persists a
 * SUCCEEDED run with a PROPOSED plan; accepting it (excluding one task, which
 * cascades to its child) creates the project, its remaining tasks, and its
 * dependency edges in one shot; dismissing a plan creates nothing.
 * {@link AnthropicClient} is faked via {@code @MockitoBean} — no real network
 * call ever happens in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "kairon.assistant.enabled=true",
        "kairon.assistant.api-key=test-only-key-not-real",
        "kairon.assistant.rate-limit.capacity=1000",
})
class ProjectPlanFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AnthropicClient anthropicClient;

    private String register(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct horse battery\","
                                + "\"displayName\":\"Flow\",\"timezone\":\"Europe/Amsterdam\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.accessToken");
    }

    private void optIntoProjectGeneration(String token) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"assistant\":{\"projectGeneration\":{\"enabled\":true}}}}"))
                .andExpect(status().isOk());
    }

    private static ProjectPlanPayload threeTaskPlan() {
        List<PlannedTaskPayload> tasks = List.of(
                new PlannedTaskPayload("t1", null, "Design", null, false, START, START.plusDays(6), null),
                new PlannedTaskPayload("t2", "t1", "Pick materials", null, false, START, START.plusDays(2), null),
                new PlannedTaskPayload("m1", null, "Design approved", null, true,
                        START.plusDays(6), START.plusDays(6), null));
        List<PlannedDependencyPayload> deps = List.of(new PlannedDependencyPayload("t1", "m1", "FS", 0));
        return new ProjectPlanPayload("Kitchen remodel", "A full remodel", "M", tasks, deps);
    }

    @Test
    void requestingAPlanWithoutOptingInIs403() throws Exception {
        String token = register("plan-flow-no-optin@example.com");

        mvc.perform(post("/api/v1/assistant/project-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + START + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullFlowGenerateAcceptWithExclusion() throws Exception {
        String token = register("plan-flow@example.com");
        optIntoProjectGeneration(token);
        when(anthropicClient.generateProjectPlan(any()))
                .thenReturn(new ProjectPlanResult(threeTaskPlan(), "claude-sonnet-5", 640, 890));

        MvcResult runResult = mvc.perform(post("/api/v1/assistant/project-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + START + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.suggestedProject.status").value("PROPOSED"))
                .andExpect(jsonPath("$.suggestedProject.tasks.length()").value(3))
                .andReturn();
        String body = runResult.getResponse().getContentAsString();
        String suggestedProjectId = JsonPath.read(body, "$.suggestedProject.id");

        // Exclude t1 -> cascades to t2 (its child); m1 and the t1->m1 dependency drop too
        // (predecessor excluded), leaving just m1... but m1 has no parent so it survives
        // as a task while its dependency on t1 is dropped.
        MvcResult accepted = mvc.perform(post(
                        "/api/v1/assistant/suggested-projects/" + suggestedProjectId + ":accept")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"excludedTaskKeys\":[\"t1\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kitchen remodel"))
                .andReturn();
        String projectId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Design approved"));

        mvc.perform(get("/api/v1/projects/" + projectId + "/dependencies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Accepting the same plan twice is a conflict, not a second project.
        mvc.perform(post("/api/v1/assistant/suggested-projects/" + suggestedProjectId + ":accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void dismissCreatesNothing() throws Exception {
        String token = register("plan-flow-dismiss@example.com");
        optIntoProjectGeneration(token);
        when(anthropicClient.generateProjectPlan(any()))
                .thenReturn(new ProjectPlanResult(threeTaskPlan(), "claude-sonnet-5", 100, 100));

        MvcResult runResult = mvc.perform(post("/api/v1/assistant/project-plan")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + START + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String suggestedProjectId = JsonPath.read(runResult.getResponse().getContentAsString(),
                "$.suggestedProject.id");

        mvc.perform(post("/api/v1/assistant/suggested-projects/" + suggestedProjectId + ":dismiss")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void anotherUsersSuggestedPlanIs404() throws Exception {
        String ownerToken = register("plan-flow-owner@example.com");
        optIntoProjectGeneration(ownerToken);
        when(anthropicClient.generateProjectPlan(any()))
                .thenReturn(new ProjectPlanResult(threeTaskPlan(), "claude-sonnet-5", 100, 100));
        MvcResult runResult = mvc.perform(post("/api/v1/assistant/project-plan")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + START + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String suggestedProjectId = JsonPath.read(runResult.getResponse().getContentAsString(),
                "$.suggestedProject.id");

        String otherToken = register("plan-flow-other@example.com");
        mvc.perform(post("/api/v1/assistant/suggested-projects/" + suggestedProjectId + ":dismiss")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}
