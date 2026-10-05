package com.kairon.assistant.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.AssistantRunPage;
import com.kairon.assistant.app.AssistantRunService;
import com.kairon.assistant.app.AssistantRunView;
import com.kairon.assistant.app.ReflectionGenerationService;
import com.kairon.assistant.app.SummaryGenerationService;
import com.kairon.assistant.web.AssistantDtos.JournalReflectionRequest;
import com.kairon.assistant.web.AssistantDtos.SummaryRequest;
import com.kairon.assistant.web.AssistantDtos.TodoSuggestionRequest;
import com.kairon.common.security.CurrentUser;
import com.kairon.common.security.UserId;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AssistantRunController {

    private static final Logger log = LoggerFactory.getLogger(AssistantRunController.class);

    private final AssistantRunService assistantRuns;
    private final SummaryGenerationService summaryDispatcher;
    private final ReflectionGenerationService reflectionDispatcher;

    public AssistantRunController(AssistantRunService assistantRuns, SummaryGenerationService summaryDispatcher,
            ReflectionGenerationService reflectionDispatcher) {
        this.assistantRuns = assistantRuns;
        this.summaryDispatcher = summaryDispatcher;
        this.reflectionDispatcher = reflectionDispatcher;
    }

    @PostMapping("/assistant/todo-suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView suggestTodos(@CurrentUser UserId userId, @Valid @RequestBody TodoSuggestionRequest req) {
        log.debug("POST /assistant/todo-suggestions userId={} day={} horizon={}",
                userId.value(), req.day(), req.horizon());
        return assistantRuns.requestTodoSuggestions(userId, req.day(), req.horizon());
    }

    @PostMapping("/assistant/summaries")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView requestSummary(@CurrentUser UserId userId, @Valid @RequestBody SummaryRequest req) {
        log.debug("POST /assistant/summaries userId={} period={} date={}", userId.value(), req.period(), req.date());
        AssistantRunView view = assistantRuns.requestSummary(userId, req.period(), req.date());
        // Dispatched only now — after requestSummary's own transaction committed (D2).
        summaryDispatcher.generate(view.id());
        return view;
    }

    @PostMapping("/assistant/journal-reflection")
    @ResponseStatus(HttpStatus.CREATED)
    public AssistantRunView requestJournalReflection(@CurrentUser UserId userId,
            @Valid @RequestBody JournalReflectionRequest req) {
        log.debug("POST /assistant/journal-reflection userId={} weekOf={}", userId.value(), req.weekOf());
        AssistantRunView view = assistantRuns.requestJournalReflection(userId, req.weekOf());
        // Dispatched only now — after requestJournalReflection's own transaction committed (D3).
        reflectionDispatcher.generate(view.id());
        return view;
    }

    @GetMapping("/assistant/runs")
    public AssistantRunPage listRuns(@CurrentUser UserId userId,
            @RequestParam(required = false) List<String> kind,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.debug("GET /assistant/runs userId={} kind={} from={} to={} page={} pageSize={}",
                userId.value(), kind, from, to, page, pageSize);
        return assistantRuns.list(userId, kind, from, to, page, pageSize);
    }

    @GetMapping("/assistant/runs/{id}")
    public AssistantRunView getRun(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("GET /assistant/runs/{} userId={}", id, userId.value());
        return assistantRuns.get(userId, id);
    }

    @DeleteMapping("/assistant/runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRun(@CurrentUser UserId userId, @PathVariable UUID id) {
        log.debug("DELETE /assistant/runs/{} userId={}", id, userId.value());
        assistantRuns.delete(userId, id);
    }
}
