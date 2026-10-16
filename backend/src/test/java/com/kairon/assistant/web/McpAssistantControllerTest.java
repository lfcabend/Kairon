package com.kairon.assistant.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.app.Horizon;
import com.kairon.assistant.app.McpAssistantService;
import com.kairon.assistant.app.RenderedContext;
import com.kairon.assistant.app.SummaryPeriod;
import com.kairon.assistant.llm.ProjectEditPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.common.security.CurrentUserArgumentResolver;
import com.kairon.common.security.SecurityConfig;
import com.kairon.common.security.WebMvcConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Request/response DTO mapping for all ten {@code /assistant/mcp/**} endpoints. */
@WebMvcTest(McpAssistantController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class })
@ActiveProfiles("test")
class McpAssistantControllerTest {

    private static final UUID USER = UUID.fromString("018f5b3e-0000-7000-8000-000000001302");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    McpAssistantService mcp;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static RequestPostProcessor asUser() {
        return jwt().jwt(j -> j.subject(USER.toString()));
    }

    private static AssistantRunView run(String kind) {
        return new AssistantRunView(UUID.randomUUID(), kind, "SUCCEEDED", "mcp", DAY, DAY, 0, 0, null,
                Instant.parse("2026-09-21T08:00:00Z"), List.of(), null, null, null, "MCP");
    }

    @Test
    void todoSuggestionContextReturnsTheRenderedPrompt() throws Exception {
        when(mcp.readTodoSuggestionContext(any(), eq(DAY), eq(Horizon.DAY)))
                .thenReturn(new RenderedContext("system prompt", "user content"));

        mvc.perform(post("/api/v1/assistant/mcp/todo-suggestions:context").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemPrompt").value("system prompt"))
                .andExpect(jsonPath("$.userContent").value("user content"));
    }

    @Test
    void submitTodoSuggestionsReturns201WithTheRun() throws Exception {
        when(mcp.submitTodoSuggestions(any(), eq(DAY), eq(Horizon.DAY), eq("claude-opus-5"), any()))
                .thenReturn(run("TODO_SUGGESTION"));

        mvc.perform(post("/api/v1/assistant/mcp/todo-suggestions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\",\"model\":\"claude-opus-5\","
                                + "\"suggestions\":[{\"title\":\"Order hardware\",\"rationale\":\"r\","
                                + "\"suggestedForDay\":\"" + DAY + "\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("TODO_SUGGESTION"))
                .andExpect(jsonPath("$.source").value("MCP"));
    }

    @Test
    void projectPlanContextReturnsTheRenderedPrompt() throws Exception {
        when(mcp.readProjectPlanContext(any(), eq("Kitchen remodel"), eq(DAY), eq(null)))
                .thenReturn(new RenderedContext("system prompt", "user content"));

        mvc.perform(post("/api/v1/assistant/mcp/project-plan:context").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + DAY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemPrompt").value("system prompt"));
    }

    @Test
    void submitProjectPlanReturns201WithTheRunAndItsPlan() throws Exception {
        when(mcp.submitProjectPlan(any(), eq("Kitchen remodel"), eq(DAY), eq(null), eq(null),
                any(ProjectPlanPayload.class))).thenReturn(run("PROJECT_GENERATION"));

        mvc.perform(post("/api/v1/assistant/mcp/project-plan").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Kitchen remodel\",\"startDate\":\"" + DAY + "\","
                                + "\"plan\":{\"name\":\"Kitchen remodel\",\"tasks\":[],\"dependencies\":[]}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("PROJECT_GENERATION"));
    }

    @Test
    void projectEditContextReturnsTheRenderedPrompt() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(mcp.readProjectEditContext(any(), eq(projectId), eq("add a task")))
                .thenReturn(new RenderedContext("system prompt", "user content"));

        mvc.perform(post("/api/v1/assistant/mcp/project-edits:context").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"description\":\"add a task\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userContent").value("user content"));
    }

    @Test
    void submitProjectEditReturns201WithTheRunAndItsDiff() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(mcp.submitProjectEdit(any(), eq(projectId), eq("add a task"), eq(null), any(ProjectEditPayload.class)))
                .thenReturn(run("PROJECT_EDIT"));

        mvc.perform(post("/api/v1/assistant/mcp/project-edits").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId + "\",\"description\":\"add a task\","
                                + "\"diff\":{\"taskOperations\":[],\"dependencyOperations\":[],"
                                + "\"reorderOperations\":[]}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("PROJECT_EDIT"));
    }

    @Test
    void summaryContextReturnsTheRenderedPrompt() throws Exception {
        when(mcp.readSummaryContext(any(), eq(SummaryPeriod.WEEK), eq(DAY)))
                .thenReturn(new RenderedContext("system prompt", "user content"));

        mvc.perform(post("/api/v1/assistant/mcp/summaries:context").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"WEEK\",\"date\":\"" + DAY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemPrompt").value("system prompt"));
    }

    @Test
    void submitSummaryReturns201WithTheRun() throws Exception {
        when(mcp.submitSummary(any(), eq(SummaryPeriod.WEEK), eq(DAY), eq(null), eq("It went well.")))
                .thenReturn(run("WEEKLY_SUMMARY"));

        mvc.perform(post("/api/v1/assistant/mcp/summaries").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"WEEK\",\"date\":\"" + DAY + "\",\"markdown\":\"It went well.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("WEEKLY_SUMMARY"));
    }

    @Test
    void journalReflectionContextReturnsTheRenderedPrompt() throws Exception {
        when(mcp.readJournalReflectionContext(any(), eq(DAY)))
                .thenReturn(new RenderedContext("system prompt", "user content"));

        mvc.perform(post("/api/v1/assistant/mcp/journal-reflection:context").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekOf\":\"" + DAY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemPrompt").value("system prompt"));
    }

    @Test
    void submitJournalReflectionReturns201WithTheRun() throws Exception {
        when(mcp.submitJournalReflection(any(), eq(DAY), eq(null), eq("A quiet week.")))
                .thenReturn(run("JOURNAL_REFLECTION"));

        mvc.perform(post("/api/v1/assistant/mcp/journal-reflection").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekOf\":\"" + DAY + "\",\"markdown\":\"A quiet week.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("JOURNAL_REFLECTION"));
    }

    @Test
    void noTokenIsProblemJson401() throws Exception {
        mvc.perform(post("/api/v1/assistant/mcp/todo-suggestions:context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"" + DAY + "\",\"horizon\":\"DAY\"}"))
                .andExpect(status().isUnauthorized());
    }
}
