package com.kairon.assistant;

import com.jayway.jsonpath.JsonPath;

import com.kairon.assistant.llm.AnthropicClient;
import com.kairon.assistant.llm.AnthropicClient.ProjectEditResult;
import com.kairon.assistant.llm.DependencyOperationPayload;
import com.kairon.assistant.llm.ProjectEditPayload;
import com.kairon.assistant.llm.ProjectFieldChangesPayload;
import com.kairon.assistant.llm.TaskOperationPayload;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The M9.5 acceptance flow (docs/milestones/M9.5-ai-project-editing.md): a
 * user not opted in gets 403; a request against an unowned project 404s
 * before any run is created; opting in and requesting an edit against a real,
 * already-seeded project persists a SUCCEEDED run with a PROPOSED diff;
 * accepting it adds a task, updates another, removes a third (cascading its
 * dependency edge), adds a new dependency, and changes the category, all in
 * one action; dismissing a diff changes nothing. {@link AnthropicClient} is
 * faked via {@code @MockitoBean} — no real network call ever happens in CI.
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
class ProjectEditFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

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

    private void optIntoProjectEditing(String token) throws Exception {
        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":{\"assistant\":{\"projectEditing\":{\"enabled\":true}}}}"))
                .andExpect(status().isOk());
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

    private String createTask(String token, String projectId, String name) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void requestingAnEditWithoutOptingInIs403() throws Exception {
        String token = register("edit-flow-no-optin@example.com");
        String projectId = createProject(token, "Kitchen remodel");

        mvc.perform(post("/api/v1/assistant/project-edits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"description\":\"add a task\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestingAnEditForAnUnownedProjectIs404BeforeCreatingARun() throws Exception {
        String ownerToken = register("edit-flow-owner-404@example.com");
        String projectId = createProject(ownerToken, "Kitchen remodel");
        String otherToken = register("edit-flow-other-404@example.com");
        optIntoProjectEditing(otherToken);

        mvc.perform(post("/api/v1/assistant/project-edits")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"description\":\"add a task\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullFlowGenerateAcceptAppliesEveryKindOfOperation() throws Exception {
        String token = register("edit-flow@example.com");
        optIntoProjectEditing(token);
        String projectId = createProject(token, "Kitchen remodel");
        String keepId = createTask(token, projectId, "Design");
        String removeId = createTask(token, projectId, "Old plumbing");
        mvc.perform(post("/api/v1/tasks/" + removeId + "/dependencies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorId\":\"" + keepId + "\"}"))
                .andExpect(status().isCreated());

        ProjectFieldChangesPayload projectChanges = new ProjectFieldChangesPayload(null, null, null, null, null,
                "Renovation");
        TaskOperationPayload addTask = new TaskOperationPayload("ADD", null, "n1", null, "Final inspection", null,
                false, null, null, null);
        TaskOperationPayload updateTask = new TaskOperationPayload("UPDATE", keepId, null, null, "Design (revised)",
                null, false, null, null, null);
        TaskOperationPayload removeTask = new TaskOperationPayload("REMOVE", removeId, null, null, null, null,
                false, null, null, null);
        DependencyOperationPayload addDependency = new DependencyOperationPayload("ADD", null, keepId, "n1", "FS", 0);
        ProjectEditPayload diff = new ProjectEditPayload(projectChanges,
                java.util.List.of(addTask, updateTask, removeTask), java.util.List.of(addDependency),
                java.util.List.of());
        when(anthropicClient.generateProjectEdit(any()))
                .thenReturn(new ProjectEditResult(diff, "claude-sonnet-5", 900, 300));

        MvcResult runResult = mvc.perform(post("/api/v1/assistant/project-edits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId
                                + "\",\"description\":\"add inspection, rename design, drop plumbing\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.suggestedProjectEdit.status").value("PROPOSED"))
                .andExpect(jsonPath("$.suggestedProjectEdit.taskOperations.length()").value(3))
                // No existing category named "Renovation" yet, so it comes back unmatched.
                .andExpect(jsonPath("$.suggestedProjectEdit.projectChanges.categoryId")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.suggestedProjectEdit.projectChanges.categoryName").value("Renovation"))
                .andReturn();
        String suggestedEditId = JsonPath.read(runResult.getResponse().getContentAsString(),
                "$.suggestedProjectEdit.id");

        mvc.perform(post("/api/v1/assistant/suggested-project-edits/" + suggestedEditId + ":accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(org.hamcrest.Matchers.notNullValue()));

        MvcResult tasksResult = mvc.perform(get("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();
        String tasksBody = tasksResult.getResponse().getContentAsString();
        java.util.List<String> names = JsonPath.read(tasksBody, "$.content[*].name");
        org.assertj.core.api.Assertions.assertThat(names)
                .containsExactlyInAnyOrder("Design (revised)", "Final inspection");

        mvc.perform(get("/api/v1/projects/" + projectId + "/dependencies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Accepting the same diff twice is a conflict, not a double-apply.
        mvc.perform(post("/api/v1/assistant/suggested-project-edits/" + suggestedEditId + ":accept")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void dismissChangesNothing() throws Exception {
        String token = register("edit-flow-dismiss@example.com");
        optIntoProjectEditing(token);
        String projectId = createProject(token, "Kitchen remodel");
        createTask(token, projectId, "Design");

        TaskOperationPayload addTask = new TaskOperationPayload("ADD", null, "n1", null, "Final inspection", null,
                false, null, null, null);
        ProjectEditPayload diff = new ProjectEditPayload(null, java.util.List.of(addTask), java.util.List.of(),
                java.util.List.of());
        when(anthropicClient.generateProjectEdit(any()))
                .thenReturn(new ProjectEditResult(diff, "claude-sonnet-5", 100, 100));

        MvcResult runResult = mvc.perform(post("/api/v1/assistant/project-edits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"description\":\"add inspection\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String suggestedEditId = JsonPath.read(runResult.getResponse().getContentAsString(),
                "$.suggestedProjectEdit.id");

        mvc.perform(post("/api/v1/assistant/suggested-project-edits/" + suggestedEditId + ":dismiss")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        mvc.perform(get("/api/v1/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void anotherUsersSuggestedEditIs404() throws Exception {
        String ownerToken = register("edit-flow-owner@example.com");
        optIntoProjectEditing(ownerToken);
        String projectId = createProject(ownerToken, "Kitchen remodel");
        createTask(ownerToken, projectId, "Design");

        TaskOperationPayload addTask = new TaskOperationPayload("ADD", null, "n1", null, "Final inspection", null,
                false, null, null, null);
        ProjectEditPayload diff = new ProjectEditPayload(null, java.util.List.of(addTask), java.util.List.of(),
                java.util.List.of());
        when(anthropicClient.generateProjectEdit(any()))
                .thenReturn(new ProjectEditResult(diff, "claude-sonnet-5", 100, 100));
        MvcResult runResult = mvc.perform(post("/api/v1/assistant/project-edits")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"description\":\"add inspection\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String suggestedEditId = JsonPath.read(runResult.getResponse().getContentAsString(),
                "$.suggestedProjectEdit.id");

        String otherToken = register("edit-flow-other@example.com");
        mvc.perform(post("/api/v1/assistant/suggested-project-edits/" + suggestedEditId + ":dismiss")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}
