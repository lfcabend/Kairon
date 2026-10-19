package com.kairon.assistant.web;

import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.app.McpAssistantService;
import com.kairon.assistant.app.ProjectEditApplyResult;
import com.kairon.assistant.app.RenderedContext;
import com.kairon.assistant.web.McpAssistantDtos.ApplyProjectEditRequest;
import com.kairon.assistant.web.McpAssistantDtos.JournalReflectionContextRequest;
import com.kairon.assistant.web.McpAssistantDtos.McpContextResponse;
import com.kairon.assistant.web.McpAssistantDtos.ProjectEditApplyResponse;
import com.kairon.assistant.web.McpAssistantDtos.ProjectEditContextRequest;
import com.kairon.assistant.web.McpAssistantDtos.ProjectPlanContextRequest;
import com.kairon.assistant.web.McpAssistantDtos.SubmitJournalReflectionRequest;
import com.kairon.assistant.web.McpAssistantDtos.SubmitProjectEditRequest;
import com.kairon.assistant.web.McpAssistantDtos.SubmitProjectPlanRequest;
import com.kairon.assistant.web.McpAssistantDtos.SubmitSummaryRequest;
import com.kairon.assistant.web.McpAssistantDtos.SubmitTodoSuggestionsRequest;
import com.kairon.assistant.web.McpAssistantDtos.SummaryContextRequest;
import com.kairon.assistant.web.McpAssistantDtos.TodoSuggestionContextRequest;
import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The ten MCP entry-point endpoints (docs/milestones/M10.5-mcp-server.md
 * §4.3/§5) — a context-read half (no {@code assistant_run} row, D8) and a
 * write-back half (the same {@code PROPOSED}/{@code SUCCEEDED} rows the
 * Anthropic-API path creates, D4) for each of the five assistant features.
 * Reachable with either a normal JWT or a personal access token (a PAT is
 * additionally restricted to only these paths plus read-only {@code todo}/
 * {@code journal}/{@code projects} — {@code SecurityConfig} D3/D17); nothing
 * here is PAT-specific on its own.
 *
 * <p>Plus one eleventh endpoint, {@code project-edits:apply} — a direct-apply
 * twin of {@code project-edits} for callers who want the Claude Code
 * conversation itself to be the review step, skipping the normal
 * {@code PROPOSED}-then-accept-in-the-web-UI flow entirely. It still writes
 * the exact same {@code assistant_run}/{@code assistant_suggested_project_edit}
 * audit rows {@code project-edits} does, immediately followed by the same
 * accept logic the web UI's "Edit with AI" button triggers.</p>
 */
@RestController
@RequestMapping("/api/v1/assistant/mcp")
public class McpAssistantController {

    private static final Logger log = LoggerFactory.getLogger(McpAssistantController.class);

    private final McpAssistantService mcp;

    public McpAssistantController(McpAssistantService mcp) {
        this.mcp = mcp;
    }

    @PostMapping("/todo-suggestions:context")
    public McpContextResponse todoSuggestionContext(@CurrentUser UserId userId,
            @Valid @RequestBody TodoSuggestionContextRequest req) {
        log.debug("POST /assistant/mcp/todo-suggestions:context userId={} day={} horizon={}",
                userId.value(), req.day(), req.horizon());
        RenderedContext context = mcp.readTodoSuggestionContext(userId, req.day(), req.horizon());
        return new McpContextResponse(context.systemPrompt(), context.userContent());
    }

    @PostMapping("/todo-suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView submitTodoSuggestions(@CurrentUser UserId userId,
            @Valid @RequestBody SubmitTodoSuggestionsRequest req) {
        log.debug("POST /assistant/mcp/todo-suggestions userId={} day={} horizon={} suggestions={}",
                userId.value(), req.day(), req.horizon(), req.suggestions().size());
        return mcp.submitTodoSuggestions(userId, req.day(), req.horizon(), req.model(), req.suggestions());
    }

    @PostMapping("/project-plan:context")
    public McpContextResponse projectPlanContext(@CurrentUser UserId userId,
            @Valid @RequestBody ProjectPlanContextRequest req) {
        log.debug("POST /assistant/mcp/project-plan:context userId={} startDate={}", userId.value(),
                req.startDate());
        RenderedContext context = mcp.readProjectPlanContext(userId, req.description(), req.startDate(),
                req.targetDeadline());
        return new McpContextResponse(context.systemPrompt(), context.userContent());
    }

    @PostMapping("/project-plan")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView submitProjectPlan(@CurrentUser UserId userId,
            @Valid @RequestBody SubmitProjectPlanRequest req) {
        log.debug("POST /assistant/mcp/project-plan userId={} startDate={}", userId.value(), req.startDate());
        return mcp.submitProjectPlan(userId, req.description(), req.startDate(), req.targetDeadline(), req.model(),
                req.plan());
    }

    @PostMapping("/project-edits:context")
    public McpContextResponse projectEditContext(@CurrentUser UserId userId,
            @Valid @RequestBody ProjectEditContextRequest req) {
        log.debug("POST /assistant/mcp/project-edits:context userId={} projectId={}", userId.value(),
                req.projectId());
        RenderedContext context = mcp.readProjectEditContext(userId, req.projectId(), req.description());
        return new McpContextResponse(context.systemPrompt(), context.userContent());
    }

    @PostMapping("/project-edits")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView submitProjectEdit(@CurrentUser UserId userId,
            @Valid @RequestBody SubmitProjectEditRequest req) {
        log.debug("POST /assistant/mcp/project-edits userId={} projectId={}", userId.value(), req.projectId());
        return mcp.submitProjectEdit(userId, req.projectId(), req.description(), req.model(), req.diff());
    }

    @PostMapping("/project-edits:apply")
    @ResponseStatus(HttpStatus.OK)
    public ProjectEditApplyResponse applyProjectEdit(@CurrentUser UserId userId,
            @Valid @RequestBody ApplyProjectEditRequest req) {
        log.debug("POST /assistant/mcp/project-edits:apply userId={} projectId={}", userId.value(), req.projectId());
        ProjectEditApplyResult result = mcp.applyProjectEdit(userId, req.projectId(), req.description(), req.model(),
                req.diff());
        return new ProjectEditApplyResponse(result.project(), result.tasks());
    }

    @PostMapping("/summaries:context")
    public McpContextResponse summaryContext(@CurrentUser UserId userId,
            @Valid @RequestBody SummaryContextRequest req) {
        log.debug("POST /assistant/mcp/summaries:context userId={} period={} date={}", userId.value(), req.period(),
                req.date());
        RenderedContext context = mcp.readSummaryContext(userId, req.period(), req.date());
        return new McpContextResponse(context.systemPrompt(), context.userContent());
    }

    @PostMapping("/summaries")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView submitSummary(@CurrentUser UserId userId, @Valid @RequestBody SubmitSummaryRequest req) {
        log.debug("POST /assistant/mcp/summaries userId={} period={} date={}", userId.value(), req.period(),
                req.date());
        return mcp.submitSummary(userId, req.period(), req.date(), req.model(), req.markdown());
    }

    @PostMapping("/journal-reflection:context")
    public McpContextResponse journalReflectionContext(@CurrentUser UserId userId,
            @Valid @RequestBody JournalReflectionContextRequest req) {
        log.debug("POST /assistant/mcp/journal-reflection:context userId={} weekOf={}", userId.value(),
                req.weekOf());
        RenderedContext context = mcp.readJournalReflectionContext(userId, req.weekOf());
        return new McpContextResponse(context.systemPrompt(), context.userContent());
    }

    @PostMapping("/journal-reflection")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView submitJournalReflection(@CurrentUser UserId userId,
            @Valid @RequestBody SubmitJournalReflectionRequest req) {
        log.debug("POST /assistant/mcp/journal-reflection userId={} weekOf={}", userId.value(), req.weekOf());
        return mcp.submitJournalReflection(userId, req.weekOf(), req.model(), req.markdown());
    }
}
